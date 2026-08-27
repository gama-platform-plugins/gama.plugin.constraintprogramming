package gama.plugin.constraintprogramming.highs;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

/**
 * The subset of the HiGHS C interface this plugin uses, bound through JNA.
 *
 * <p>
 * The signatures follow <em>highs_c_api.h</em> of HiGHS 1.15. A {@code HighsInt} is a 32 bit integer in the standard
 * build, which is what the distributed binaries are; a build configured for 64 bit indices would need these to be
 * {@code long} instead.
 * </p>
 */
public interface HighsLibrary extends Library {

	/** The name of the shared library, without prefix or extension. */
	String NAME = "highs";

	// Constants of the C interface, copied from highs_c_api.h

	/** Compressed sparse column form. */
	int MATRIX_FORMAT_COLWISE = 1;

	/** Compressed sparse row form, which is how a set of constraints is naturally laid out. */
	int MATRIX_FORMAT_ROWWISE = 2;

	/** Minimise the objective. */
	int OBJ_SENSE_MINIMIZE = 1;

	/** Maximise the objective. */
	int OBJ_SENSE_MAXIMIZE = -1;

	/** A continuous column. */
	int VAR_TYPE_CONTINUOUS = 0;

	/** An integral column. */
	int VAR_TYPE_INTEGER = 1;

	/** The model was solved to optimality. */
	int MODEL_STATUS_OPTIMAL = 7;

	/** The model has no feasible solution. */
	int MODEL_STATUS_INFEASIBLE = 8;

	/** The objective is unbounded. */
	int MODEL_STATUS_UNBOUNDED = 10;

	/** The solver stopped on its time limit. */
	int MODEL_STATUS_TIME_LIMIT = 13;

	/** The solver stopped on its iteration limit. */
	int MODEL_STATUS_ITERATION_LIMIT = 14;

	/** The solver was interrupted. */
	int MODEL_STATUS_INTERRUPT = 17;

	/**
	 * Creates a solver instance.
	 *
	 * @return an opaque handle, null on failure
	 */
	Pointer Highs_create();

	/**
	 * Releases a solver instance.
	 *
	 * @param highs
	 *            the handle
	 */
	void Highs_destroy(Pointer highs);

	/**
	 * Sets a boolean option, such as output_flag.
	 *
	 * @param highs
	 *            the handle
	 * @param option
	 *            the name of the option
	 * @param value
	 *            zero for false, anything else for true
	 * @return a status code, zero on success
	 */
	int Highs_setBoolOptionValue(Pointer highs, String option, int value);

	/**
	 * Sets a real-valued option, such as time_limit.
	 *
	 * @param highs
	 *            the handle
	 * @param option
	 *            the name of the option
	 * @param value
	 *            the value
	 * @return a status code, zero on success
	 */
	int Highs_setDoubleOptionValue(Pointer highs, String option, double value);

	/**
	 * Hands over a linear program.
	 *
	 * @param highs
	 *            the handle
	 * @param numCol
	 *            the number of columns
	 * @param numRow
	 *            the number of rows
	 * @param numNz
	 *            the number of non-zero entries of the matrix
	 * @param aFormat
	 *            the layout of the matrix
	 * @param sense
	 *            the direction of the optimisation
	 * @param offset
	 *            the constant part of the objective
	 * @param colCost
	 *            the objective coefficient of each column
	 * @param colLower
	 *            the lower bound of each column
	 * @param colUpper
	 *            the upper bound of each column
	 * @param rowLower
	 *            the lower bound of each row
	 * @param rowUpper
	 *            the upper bound of each row
	 * @param aStart
	 *            the index at which each row starts in the two arrays below
	 * @param aIndex
	 *            the column of each entry
	 * @param aValue
	 *            the value of each entry
	 * @return a status code, zero on success
	 */
	int Highs_passLp(Pointer highs, int numCol, int numRow, int numNz, int aFormat, int sense, double offset,
			double[] colCost, double[] colLower, double[] colUpper, double[] rowLower, double[] rowUpper,
			int[] aStart, int[] aIndex, double[] aValue);

	/**
	 * Hands over a mixed integer program, which is a linear program plus the integrality of each column.
	 *
	 * @param highs
	 *            the handle
	 * @param numCol
	 *            the number of columns
	 * @param numRow
	 *            the number of rows
	 * @param numNz
	 *            the number of non-zero entries of the matrix
	 * @param aFormat
	 *            the layout of the matrix
	 * @param sense
	 *            the direction of the optimisation
	 * @param offset
	 *            the constant part of the objective
	 * @param colCost
	 *            the objective coefficient of each column
	 * @param colLower
	 *            the lower bound of each column
	 * @param colUpper
	 *            the upper bound of each column
	 * @param rowLower
	 *            the lower bound of each row
	 * @param rowUpper
	 *            the upper bound of each row
	 * @param aStart
	 *            the index at which each row starts
	 * @param aIndex
	 *            the column of each entry
	 * @param aValue
	 *            the value of each entry
	 * @param integrality
	 *            the kind of each column
	 * @return a status code, zero on success
	 */
	int Highs_passMip(Pointer highs, int numCol, int numRow, int numNz, int aFormat, int sense, double offset,
			double[] colCost, double[] colLower, double[] colUpper, double[] rowLower, double[] rowUpper,
			int[] aStart, int[] aIndex, double[] aValue, int[] integrality);

	/**
	 * Runs the solver.
	 *
	 * @param highs
	 *            the handle
	 * @return a status code, zero on success
	 */
	int Highs_run(Pointer highs);

	/**
	 * Returns what the solver concluded.
	 *
	 * @param highs
	 *            the handle
	 * @return one of the model status constants
	 */
	int Highs_getModelStatus(Pointer highs);

	/**
	 * Reads the solution back.
	 *
	 * @param highs
	 *            the handle
	 * @param colValue
	 *            filled with the value of each column
	 * @param colDual
	 *            filled with the reduced cost of each column, may be null
	 * @param rowValue
	 *            filled with the activity of each row, may be null
	 * @param rowDual
	 *            filled with the dual of each row, may be null
	 * @return a status code, zero on success
	 */
	int Highs_getSolution(Pointer highs, double[] colValue, double[] colDual, double[] rowValue, double[] rowDual);

	/**
	 * Returns the value of the objective in the solution found.
	 *
	 * @param highs
	 *            the handle
	 * @return the objective value
	 */
	double Highs_getObjectiveValue(Pointer highs);

	/**
	 * The wall clock time the last run took, in seconds.
	 *
	 * @param highs
	 *            the model
	 * @return the time in seconds
	 */
	double Highs_getRunTime(Pointer highs);

	/**
	 * Reads one of the integer figures the solver reports about its last run.
	 *
	 * @param highs
	 *            the model
	 * @param info
	 *            the name of the figure, such as {@code simplex_iteration_count}
	 * @param value
	 *            a one element array the figure is written into
	 * @return 0 when the figure exists
	 */
	int Highs_getIntInfoValue(Pointer highs, String info, int[] value);

	/**
	 * Reads one of the long figures the solver reports about its last run.
	 *
	 * @param highs
	 *            the model
	 * @param info
	 *            the name of the figure, such as {@code mip_node_count}
	 * @param value
	 *            a one element array the figure is written into
	 * @return 0 when the figure exists
	 */
	int Highs_getInt64InfoValue(Pointer highs, String info, long[] value);

}
