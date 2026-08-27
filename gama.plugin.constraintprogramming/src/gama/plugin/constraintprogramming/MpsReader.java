package gama.plugin.constraintprogramming;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.chocosolver.solver.variables.IntVar;

import gama.api.exceptions.GamaRuntimeException;
import gama.api.runtime.scope.IScope;
import gama.api.utils.files.FileUtils;
import gama.plugin.constraintprogramming.terms.Relation;
import gama.plugin.constraintprogramming.terms.Term;

/**
 * Reads a problem written in the MPS format, the interchange format of linear and mixed integer programming.
 *
 * <p>
 * The reader produces the same neutral terms as a model written by hand, so a file can be given to whichever engine the
 * problem is created with. The sections understood are NAME, OBJSENSE, ROWS, COLUMNS with its INTORG and INTEND
 * markers, RHS, RANGES, BOUNDS and ENDATA. Both the fixed and the free layout are read, since fields are split on
 * whitespace rather than on column positions.
 * </p>
 */
public class MpsReader {

	/** The sense of a row. */
	private enum RowType {
		/** The objective, or any other free row. */
		FREE,
		/** Lower or equal. */
		LEQ,
		/** Greater or equal. */
		GEQ,
		/** Equal. */
		EQ
	}

	/** A row being read: its sense, its coefficients by column, its right hand side and its optional range. */
	private static final class Row {

		/** The sense. */
		RowType type;

		/** The coefficient of each column mentioned in the row. */
		final Map<String, Double> coefficients = new LinkedHashMap<>();

		/** The right hand side, zero unless the RHS section says otherwise. */
		double rhs;

		/** The range, null unless the RANGES section makes the row two-sided. */
		Double range;
	}

	/** A column being read: its bounds and whether it is integral. */
	private static final class Column {

		/** The lower bound, zero by default as the format prescribes. */
		double lower;

		/** The upper bound, null while none has been given. */
		Double upper;

		/** Whether the column is integral. */
		boolean integral;

		/** Whether a lower bound has been given explicitly. */
		boolean lowerGiven;
	}

	/** The rows, by name, in declaration order. */
	private final Map<String, Row> rows = new LinkedHashMap<>();

	/** The columns, by name, in declaration order. */
	private final Map<String, Column> columns = new LinkedHashMap<>();

	/** The name of the objective row. */
	private String objectiveRow;

	/** Whether the objective has to be maximised. */
	private boolean maximises;

	/** The name given in the NAME section. */
	private String name = "mps";

	/**
	 * Reads a file and builds the corresponding problem.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @param path
	 *            the path of the file
	 * @param backend
	 *            the engine the problem has to be solved with
	 * @return the problem, with its variables declared, its constraints posted and its objective set
	 */
	public static GamaProblem read(final IScope scope, final String path, final GamaProblem.Backend backend)
			throws GamaRuntimeException {
		// Paths in a model are relative to the model, not to the working directory of the platform
		return readResolved(scope, FileUtils.constructAbsoluteFilePath(scope, path, true), backend);
	}

	/**
	 * Reads a file whose path has already been resolved to an absolute one.
	 *
	 * @param scope
	 *            the current scope, used to report the error
	 * @param path
	 *            the absolute path of the file
	 * @param backend
	 *            the engine the problem has to be solved with
	 * @return the problem
	 */
	public static GamaProblem readResolved(final IScope scope, final String path, final GamaProblem.Backend backend)
			throws GamaRuntimeException {
		final MpsReader reader = new MpsReader();
		try (BufferedReader in = open(path)) {
			reader.parse(in);
		} catch (final IOException e) {
			throw GamaRuntimeException.error("Impossible to read " + path + ": " + e.getMessage(), scope);
		}
		return reader.build(scope, backend);
	}

	/**
	 * Opens the file. MPS is an ASCII format, read here as Latin-1 so that a stray byte in an old file does not fail
	 * the whole read.
	 *
	 * @param path
	 *            the resolved, absolute path
	 * @return a reader over the content
	 */
	private static BufferedReader open(final String path) throws IOException {
		return new BufferedReader(
				new InputStreamReader(Files.newInputStream(Path.of(path)), StandardCharsets.ISO_8859_1));
	}

	/**
	 * Reads the sections of the file.
	 *
	 * @param in
	 *            the reader
	 */
	private void parse(final BufferedReader in) throws IOException {
		String section = "";
		boolean integral = false;
		String line;
		while ((line = in.readLine()) != null) {
			if (line.isBlank() || line.charAt(0) == '*') { continue; }
			final boolean isHeader = !Character.isWhitespace(line.charAt(0));
			final String[] f = line.trim().split("\\s+");
			if (isHeader) {
				section = f[0].toUpperCase(Locale.ROOT);
				if ("NAME".equals(section) && f.length > 1) { name = f[1]; }
				if ("ENDATA".equals(section)) return;
				continue;
			}
			switch (section) {
				case "OBJSENSE" -> maximises = f[0].toUpperCase(Locale.ROOT).startsWith("MAX");
				case "ROWS" -> readRow(f);
				case "COLUMNS" -> integral = readColumn(f, integral);
				case "RHS" -> readRhs(f);
				case "RANGES" -> readRanges(f);
				case "BOUNDS" -> readBound(f);
				default -> { /* the body of NAME and any unknown section are ignored */ }
			}
		}
	}

	/**
	 * Reads a line of the ROWS section.
	 *
	 * @param f
	 *            the fields of the line
	 */
	private void readRow(final String[] f) {
		if (f.length < 2) return;
		final Row row = new Row();
		row.type = switch (f[0].toUpperCase(Locale.ROOT)) {
			case "L" -> RowType.LEQ;
			case "G" -> RowType.GEQ;
			case "E" -> RowType.EQ;
			default -> RowType.FREE;
		};
		rows.put(f[1], row);
		// The first free row is the objective; the others are ignored, as the format prescribes
		if (row.type == RowType.FREE && objectiveRow == null) { objectiveRow = f[1]; }
	}

	/**
	 * Reads a line of the COLUMNS section, which gives the coefficients of one column in one or two rows.
	 *
	 * @param f
	 *            the fields of the line
	 * @param integral
	 *            whether the reader is currently between INTORG and INTEND
	 * @return the new integrality state
	 */
	private boolean readColumn(final String[] f, final boolean integral) {
		// A marker line carries 'MARKER' and either 'INTORG' or 'INTEND', the name in front of it being arbitrary
		for (final String field : f) {
			if ("'INTORG'".equals(field)) return true;
			if ("'INTEND'".equals(field)) return false;
		}
		final String columnName = f[0];
		final Column column = columns.computeIfAbsent(columnName, n -> new Column());
		column.integral |= integral;
		for (int i = 1; i + 1 < f.length; i += 2) {
			final Row row = rows.get(f[i]);
			if (row != null) { row.coefficients.merge(columnName, value(f[i + 1]), Double::sum); }
		}
		return integral;
	}

	/**
	 * Reads a line of the RHS section.
	 *
	 * @param f
	 *            the fields of the line
	 */
	private void readRhs(final String[] f) {
		for (int i = 1; i + 1 < f.length; i += 2) {
			final Row row = rows.get(f[i]);
			if (row != null) { row.rhs = value(f[i + 1]); }
		}
	}

	/**
	 * Reads a line of the RANGES section.
	 *
	 * @param f
	 *            the fields of the line
	 */
	private void readRanges(final String[] f) {
		for (int i = 1; i + 1 < f.length; i += 2) {
			final Row row = rows.get(f[i]);
			if (row != null) { row.range = value(f[i + 1]); }
		}
	}

	/**
	 * Reads a line of the BOUNDS section.
	 *
	 * @param f
	 *            the fields of the line
	 */
	private void readBound(final String[] f) {
		if (f.length < 3) return;
		final String kind = f[0].toUpperCase(Locale.ROOT);
		final Column column = columns.computeIfAbsent(f[2], n -> new Column());
		final double v = f.length > 3 ? value(f[3]) : 0;
		switch (kind) {
			case "UP" -> {
				column.upper = v;
				// A negative upper bound on a column whose lower bound was never given frees that lower bound
				if (!column.lowerGiven && v < 0) { column.lower = Double.NEGATIVE_INFINITY; }
			}
			case "LO" -> {
				column.lower = v;
				column.lowerGiven = true;
			}
			case "FX" -> {
				column.lower = v;
				column.upper = v;
				column.lowerGiven = true;
			}
			case "FR" -> {
				column.lower = Double.NEGATIVE_INFINITY;
				column.upper = Double.POSITIVE_INFINITY;
				column.lowerGiven = true;
			}
			case "MI" -> {
				column.lower = Double.NEGATIVE_INFINITY;
				column.lowerGiven = true;
			}
			case "PL" -> column.upper = Double.POSITIVE_INFINITY;
			case "BV" -> {
				column.lower = 0;
				column.upper = 1.0;
				column.integral = true;
				column.lowerGiven = true;
			}
			case "LI" -> {
				column.lower = v;
				column.integral = true;
				column.lowerGiven = true;
			}
			case "UI" -> {
				column.upper = v;
				column.integral = true;
			}
			default -> { /* an unknown bound type is ignored rather than fatal */ }
		}
	}

	/**
	 * Parses a number, tolerating the Fortran exponent letter still found in old files.
	 *
	 * @param s
	 *            the field
	 * @return its value, zero if it cannot be read
	 */
	private static double value(final String s) {
		try {
			return Double.parseDouble(s.replace('D', 'E').replace('d', 'e'));
		} catch (final NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Builds the problem from what has been read.
	 *
	 * @param scope
	 *            the current scope
	 * @param backend
	 *            the engine
	 * @return the problem
	 */
	private GamaProblem build(final IScope scope, final GamaProblem.Backend backend) throws GamaRuntimeException {
		final GamaProblem problem = new GamaProblem(name, backend);
		final Map<String, GamaVariable> variables = new LinkedHashMap<>();
		columns.forEach((columnName, c) -> variables.put(columnName, declare(problem, columnName, c.lower,
				c.upper == null ? Double.POSITIVE_INFINITY : c.upper, c.integral)));

		for (final Map.Entry<String, Row> e : rows.entrySet()) {
			final Row row = e.getValue();
			if (row.type == RowType.FREE) { continue; }
			final Term left = sum(scope, variables, row, e.getKey());
			for (final Relation r : relationsOf(row, left)) { new GamaConstraint(problem, r).post(scope); }
		}

		if (objectiveRow != null) {
			// The objective of an MPS file is a linear form over the columns, not a column of its own: giving it a
			// variable would need bounds the file never states.
			problem.setObjective(sum(scope, variables, rows.get(objectiveRow), objectiveRow), maximises);
		}
		return problem;
	}

	/**
	 * Declares a variable of the right kind for a column.
	 *
	 * @param problem
	 *            the problem
	 * @param columnName
	 *            the name of the column
	 * @param lb
	 *            the lower bound
	 * @param ub
	 *            the upper bound
	 * @param integral
	 *            whether the column is integral
	 * @return the variable
	 */
	private static GamaVariable declare(final GamaProblem problem, final String columnName, final double lb,
			final double ub, final boolean integral) {
		if (integral) {
			final int low = clamp(lb, true);
			final int high = clamp(ub, false);
			return problem.register(GamaVariable.ofInt(problem, columnName, low, high, (long) high - low > 65536));
		}
		return problem.register(GamaVariable.ofReal(problem, columnName, clampReal(lb, true), clampReal(ub, false)));
	}

	/**
	 * Brings an integral bound into the range Choco accepts.
	 *
	 * @param bound
	 *            the bound
	 * @param lower
	 *            whether it is a lower bound
	 * @return the clamped bound
	 */
	private static int clamp(final double bound, final boolean lower) {
		if (Double.isNaN(bound)) return lower ? IntVar.MIN_INT_BOUND : IntVar.MAX_INT_BOUND;
		if (bound <= IntVar.MIN_INT_BOUND) return IntVar.MIN_INT_BOUND;
		if (bound >= IntVar.MAX_INT_BOUND) return IntVar.MAX_INT_BOUND;
		return (int) Math.round(bound);
	}

	/**
	 * Brings a continuous bound into a finite range, an infinite one being unusable as the bound of a real variable.
	 *
	 * @param bound
	 *            the bound
	 * @param lower
	 *            whether it is a lower bound
	 * @return the clamped bound
	 */
	private static double clampReal(final double bound, final boolean lower) {
		if (Double.isNaN(bound)) return lower ? -Double.MAX_VALUE / 4 : Double.MAX_VALUE / 4;
		if (Double.isInfinite(bound)) return bound > 0 ? Double.MAX_VALUE / 4 : -Double.MAX_VALUE / 4;
		return bound;
	}

	/**
	 * Builds the weighted sum of a row.
	 *
	 * @param scope
	 *            the current scope
	 * @param variables
	 *            the variables, by column name
	 * @param row
	 *            the row
	 * @param rowName
	 *            its name, for the error message
	 * @return the term
	 */
	private static Term sum(final IScope scope, final Map<String, GamaVariable> variables, final Row row,
			final String rowName) throws GamaRuntimeException {
		final List<Term> products = new ArrayList<>(row.coefficients.size());
		for (final Map.Entry<String, Double> e : row.coefficients.entrySet()) {
			final GamaVariable v = variables.get(e.getKey());
			if (v == null) throw GamaRuntimeException
					.error("The row " + rowName + " mentions the unknown column " + e.getKey(), scope);
			products.add(new Term.Binary(Term.Bin.MUL, new Term.Const(e.getValue()), new Term.Var(v)));
		}
		return Term.sum(products);
	}

	/**
	 * Turns a row into one relation, or two when a range makes it two-sided.
	 *
	 * @param row
	 *            the row
	 * @param left
	 *            its weighted sum
	 * @return the relations to post
	 */
	private static List<Relation> relationsOf(final Row row, final Term left) {
		final List<Relation> result = new ArrayList<>(2);
		if (row.range == null) {
			result.add(new Relation(switch (row.type) {
				case LEQ -> Relation.Rel.LE;
				case GEQ -> Relation.Rel.GE;
				default -> Relation.Rel.EQ;
			}, left, new Term.Const(row.rhs)));
			return result;
		}
		final double r = row.range;
		final double low;
		final double high;
		switch (row.type) {
			case LEQ -> {
				low = row.rhs - Math.abs(r);
				high = row.rhs;
			}
			case GEQ -> {
				low = row.rhs;
				high = row.rhs + Math.abs(r);
			}
			default -> {
				low = r >= 0 ? row.rhs : row.rhs + r;
				high = r >= 0 ? row.rhs + r : row.rhs;
			}
		}
		result.add(new Relation(Relation.Rel.GE, left, new Term.Const(low)));
		result.add(new Relation(Relation.Rel.LE, left, new Term.Const(high)));
		return result;
	}

}
