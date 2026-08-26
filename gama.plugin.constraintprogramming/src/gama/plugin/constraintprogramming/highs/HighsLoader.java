package gama.plugin.constraintprogramming.highs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.sun.jna.Native;

/**
 * Finds and loads the HiGHS shared library.
 *
 * <p>
 * The binaries ship inside the plugin, under {@code native/<os>/<arch>/}. They are copied to a temporary directory and
 * loaded from there by their absolute path, so that nothing has to be added to the PATH of the machine, nor to
 * {@code java.library.path}, which the platform reads once at startup and never again.
 * </p>
 *
 * <p>
 * Loading is attempted once. When it fails, for want of a binary for this platform or of a dependency of the binary,
 * the reason is kept and reported by the engine rather than surfacing as a link error in the middle of a simulation.
 * </p>
 */
public class HighsLoader {

	/** The library once loaded, null while it has not been, or could not be. */
	private static HighsLibrary library;

	/** Why the library could not be loaded, null while no attempt has failed. */
	private static String failure;

	/** Whether an attempt has already been made. */
	private static boolean attempted;

	/** The directory the binaries are extracted to, kept so that they are copied only once. */
	private static Path extracted;

	/**
	 * Returns the library, loading it on the first call.
	 *
	 * @return the library, or null when it is not available on this platform
	 */
	public static synchronized HighsLibrary library() {
		if (!attempted) {
			attempted = true;
			try {
				library = load();
			} catch (final NoClassDefFoundError e) {
				// JNA itself is missing, which is a wiring problem rather than a native one: the bundle com.sun.jna
				// has to be visible to this plugin and present in the running platform.
				failure = "the JNA bundle is not wired to this plugin. Check that com.sun.jna is required by its "
						+ "manifest and included in the launched configuration, then restart the platform";
			} catch (final UnsatisfiedLinkError e) {
				// The binary is there but cannot be loaded: wrong architecture, or a dependency of its own missing
				failure = "the native binary could not be loaded (" + e.getMessage()
						+ "). It may be built for another architecture, or need a library this machine does not have";
			} catch (final Throwable e) {
				failure = e.getMessage() == null ? e.toString() : e.getMessage();
			}
		}
		return library;
	}

	/**
	 * Whether the engine can be used on this machine.
	 *
	 * @return true if the library is loaded
	 */
	public static boolean isAvailable() { return library() != null; }

	/**
	 * Why the library could not be loaded.
	 *
	 * @return the reason, or null if it was loaded
	 */
	public static String getFailure() {
		library();
		return failure;
	}

	/**
	 * Copies the binaries for this platform out of the plugin and loads the main one.
	 *
	 * @return the loaded library
	 */
	private static HighsLibrary load() throws IOException {
		final String os = osFolder();
		final String arch = archFolder();
		final Path directory = extractionDirectory();

		// Dependencies are loaded before the library that needs them, so that the operating system finds them already
		// in the process rather than looking for them along a search path that does not include this directory.
		Path main = null;
		for (final String file : filesFor(os)) {
			final Path copy = extract(os, arch, file, directory);
			if (copy == null) { continue; }
			if (file.contains(HighsLibrary.NAME)) {
				main = copy;
			} else {
				System.load(copy.toAbsolutePath().toString());
			}
		}
		if (main == null) { throw new IOException(whatIsMissing(os, arch)); }

		// Also declared for JNA, which is what resolves any library this one asks for by name
		System.setProperty("jna.library.path", directory.toAbsolutePath().toString());
		return Native.load(main.toAbsolutePath().toString(), HighsLibrary.class);
	}

	/**
	 * The names a shared library may bear on this platform, in the order they are tried.
	 *
	 * @param os
	 *            the folder of this operating system
	 * @return the file names
	 */
	private static List<String> filesFor(final String os) {
		return switch (os) {
			case "win32" -> List.of("highs.dll", "libhighs.dll");
			case "macosx" -> List.of("libhighs.dylib", "highs.dylib");
			default -> List.of("libhighs.so", "highs.so");
		};
	}

	/**
	 * Explains what is missing, by looking at what the plugin actually carries for this platform.
	 *
	 * <p>
	 * The distinction that matters here is between a shared library, which can be loaded at runtime, and a static
	 * archive, which cannot: a .a or a .lib is meant for a compiler and is inert once the program is running. Dropping
	 * one in place of the other is an easy mistake to make and an opaque one to diagnose, so it is named.
	 * </p>
	 *
	 * @param os
	 *            the folder of this operating system
	 * @param arch
	 *            the folder of this architecture
	 * @return the message to report
	 */
	private static String whatIsMissing(final String os, final String arch) {
		final String expected = filesFor(os).get(0);
		final String folder = "native/" + os + "/" + arch;
		final Path onDisk = besideTheClasses(folder);
		if (onDisk != null) {
			try (Stream<Path> files = Files.list(onDisk)) {
				final List<String> archives = files.map(f -> f.getFileName().toString())
						.filter(n -> n.endsWith(".a") || n.endsWith(".lib")).toList();
				if (!archives.isEmpty()) return folder + " carries " + String.join(", ", archives)
						+ ", which are static archives: they are meant for a compiler and cannot be loaded at runtime. "
						+ "A shared library is needed, named " + expected
						+ ". Take it from a HiGHS release built with shared libraries, or build one with "
						+ "-DBUILD_SHARED_LIBS=ON.";
			} catch (final IOException e) {
				// The folder cannot be listed; fall through to the plain message
			}
		}
		return "no HiGHS shared library is shipped for " + os + "/" + arch + ". Add " + expected + " under " + folder
				+ " in the plugin.";
	}

	/**
	 * Copies one file out of the plugin, if it is there.
	 *
	 * <p>
	 * The binaries are looked up twice. On the bundle classpath first, which is where they are once the plugin is
	 * built, and then on disk next to the compiled classes, which is where they stay while the plugin is run from a
	 * development workspace: there the classpath is the output folder alone, and a folder sitting at the root of the
	 * project is not on it.
	 * </p>
	 *
	 * @param os
	 *            the folder of this operating system
	 * @param arch
	 *            the folder of this architecture
	 * @param file
	 *            the name of the file
	 * @param directory
	 *            where to copy it
	 * @return the copy, or null if the plugin carries no such file
	 */
	private static Path extract(final String os, final String arch, final String file, final Path directory)
			throws IOException {
		final String relative = "native/" + os + "/" + arch + "/" + file;
		final Path target = directory.resolve(file);
		if (Files.exists(target)) return target;

		try (InputStream in = HighsLoader.class.getResourceAsStream("/" + relative)) {
			if (in != null) {
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				return target;
			}
		}
		final Path onDisk = besideTheClasses(relative);
		if (onDisk == null) return null;
		Files.copy(onDisk, target, StandardCopyOption.REPLACE_EXISTING);
		return target;
	}

	/**
	 * Looks for a file of the plugin on disk, starting from where its classes were loaded from.
	 *
	 * @param relative
	 *            the path of the file inside the plugin
	 * @return the file, or null if it is nowhere to be found
	 */
	private static Path besideTheClasses(final String relative) {
		try {
			final var source = HighsLoader.class.getProtectionDomain().getCodeSource();
			if (source == null || source.getLocation() == null) return null;
			Path base = Path.of(source.getLocation().toURI());
			// The classes come either from the bundle directory itself, or from the output folder inside the project
			for (int i = 0; i < 3 && base != null; i++) {
				final Path candidate = base.resolve(relative);
				if (Files.exists(candidate)) return candidate;
				base = base.getParent();
			}
		} catch (final Exception e) {
			// A code source that is not a plain file, such as a jar inside a jar: nothing to look for on disk
		}
		return null;
	}

	/**
	 * The directory the binaries are copied to, created on the first call.
	 *
	 * @return the directory
	 */
	private static Path extractionDirectory() throws IOException {
		if (extracted == null) {
			extracted = Files.createTempDirectory("gama-highs");
			extracted.toFile().deleteOnExit();
		}
		return extracted;
	}

	/**
	 * The folder name of the running operating system, following the convention of the other GAMA plugins that carry
	 * native code.
	 *
	 * @return the folder name
	 */
	private static String osFolder() {
		final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) return "win32";
		if (os.contains("mac") || os.contains("darwin")) return "macosx";
		return "linux";
	}

	/**
	 * The folder name of the running architecture.
	 *
	 * @return the folder name
	 */
	private static String archFolder() {
		final String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		if (arch.contains("aarch64") || arch.contains("arm64")) return "aarch64";
		return "x86_64";
	}

}
