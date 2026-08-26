# Constraint Programming for GAMA

A GAMA plugin exposing [Choco-solver](https://choco-solver.org) 6 and [HiGHS](https://highs.dev) to GAML: declare decision variables, post constraints over them, and let a solver find an assignment that satisfies them, or the best one according to an objective.

The GAML API mirrors the shape of the Choco Java API, so anything written for Choco translates line by line, and the Choco documentation applies directly. The same model can be given to a linear or mixed integer solver instead, by naming the engine when the problem is created.

This version is developed for GAMA 2026-06 and above.

## What the engines are, and when to use which

The same model can be handed to several solvers. The engine is chosen when the problem is created and nothing else in the model changes: the same declarations, the same expressions, the same way of reading a solution.

```gaml
problem p <- problem("my_problem", "highs");
```

Two families of engine sit behind that word. What each is good at follows from how it searches, so the two sections below start there.

### Constraint programming: `choco`, `choco_lcg`

A constraint engine reasons by **propagation**. Each constraint repeatedly removes values that cannot appear in any solution, the removals cascade to the neighbouring constraints, and the solver branches only on what propagation could not settle.

Its strength is combinatorial structure. A single `all_different` over twenty variables reasons about the whole set at once, and so do `circuit`, `element`, `table`, `global_cardinality`. It also handles integer arithmetic that is not linear, so a product of two decisions or a square is expressed directly. It is the only family here that can enumerate solutions rather than return one, and the only one that can reify a constraint into a boolean variable, which is how a preference becomes a term of the objective rather than a rule that can make the model infeasible.

Its weakness is dense linear arithmetic. Over a weighted sum, propagation only tightens bounds, which prunes very little, and the search thrashes. [Choco-solver](https://choco-solver.org) 6 is the engine, and `choco_lcg` is the same solver with lazy clause generation, where each conflict leaves behind a clause the search will not walk into again.

### Linear and mixed integer programming: `highs`, `lp`

A linear engine does the opposite. It drops the integrality requirement, solves the continuous relaxation with a simplex, and rebuilds integrality by branching and cutting, guided by the bound the relaxation gives it. That bound is exactly what a constraint engine lacks on this kind of model, which is why the two families are complementary rather than competing.

Its strength is any model made of weighted sums and bounds: allocation, blending, transport, production planning, network flow. Its limit is everything else. No global constraints, no product of two unknowns, no disequality, no set variables. Those are refused when written, with the operator or the sub-expression named, rather than mis-solved.

[HiGHS](https://highs.dev) is the engine to use, shipped with the plugin as a native binary. The `lp` engine is the small solver bundled inside Choco, kept because it needs nothing to run, but it works on a dense tableau, cannot be interrupted, and writes to the standard output. Prefer `highs`.

### Choosing

Look at the constraints, not at the size.

| The model is made of | Use |
|---|---|
| weighted sums, bounds, an objective | `highs` |
| `all_different`, sequencing, routing, "at most k of these" | `choco` |
| products, quotients or powers of two decisions | `choco` |
| preferences to be counted rather than imposed | `choco`, through `reify` |
| every solution wanted, not just one | `choco` |
| both kinds at once | start with `choco`, and see |

On `80bau3b`, a Netlib test problem of 9799 columns and 2262 rows, `highs` reads and solves it in about a second and returns the published optimum, while `lp` does not finish. On a model built around `all_different` or `circuit`, no linear engine can state the problem at all.

`Engine Benchmark.gaml` runs the same growing family of problems through every engine and charts both the time spent and the value found. Time alone is misleading, since an engine that returns quickly because it stopped early is only distinguishable in the second chart.

## The four steps

Every model follows the same sequence: create a problem, declare its variables, post constraints, search.

```gaml
model n_queens

global {
    int n <- 8;

    init {
        // 1. the problem
        problem p <- problem("n_queens");

        // 2. the variables: one per column, holding the row of its queen
        list<pb_variable> queens <- int_vars(p, "Q", n, 1, n);

        // 3. the constraints
        do post(all_different(queens));
        loop i from: 0 to: n - 2 {
            loop j from: i + 1 to: n - 1 {
                do post(queens[i] - queens[j] != j - i);
                do post(queens[i] - queens[j] != i - j);
            }
        }

        // 4. the search
        solution sol <- search(p, 10 #s);
        if (sol.exists) { write values_of(sol, queens); }
    }
}
```

### Building a constraint is not posting it

`all_different(queens)` builds an object representing a *relation*. `post` turns it into an *assertion*: "this relation holds in every solution". The two are separate because an unposted constraint can be reasoned about rather than enforced, which is what `reify`, `or_all`, `if_then` and `opposite` need.

```gaml
// hard: the relation must hold
do post(end_last <= 17);

// soft: we count whether it holds, and maximise that
pb_variable on_time <- reify(end_last <= 17);
solution best <- maximize(p, on_time);
```

> **Careful:** a constraint that is built and never posted is silently ignored. `do all_different(q);` compiles, runs, and does nothing. The missing `post` is not reported.

---

## Types

| Type | Wraps | Notes |
|---|---|---|
| `problem` | `org.chocosolver.solver.Model` | mutable and stateful; never copied |
| `pb_variable` | `org.chocosolver.solver.variables.Variable` | int, bool, set or real |
| `constraint` | `org.chocosolver.solver.constraints.Constraint` | inert until posted |
| `solution` | `org.chocosolver.solver.Solution` | independent snapshot of the values |

### Attributes

**`problem`**

| Attribute | Type | Meaning |
|---|---|---|
| `name` | `string` | name given at creation |
| `nb_variables` | `int` | number of variables declared |
| `nb_constraints` | `int` | number of constraints posted |
| `variables` | `list<pb_variable>` | in declaration order |
| `solutions` | `int` | solutions found by the last search |
| `search_time` | `float` | duration of the last search, in seconds |
| `nodes` | `int` | nodes explored |
| `fails` | `int` | failures encountered |

**`pb_variable`**

| Attribute | Type | Meaning |
|---|---|---|
| `name` | `string` | name in the problem |
| `kind` | `string` | `"int"`, `"bool"`, `"set"`, `"real"`, `"expression"` or `"other"` |
| `lb` / `ub` | `int` | current bounds of the domain (int and bool variables) |
| `instantiated` | `bool` | whether the domain holds a single value |
| `value` | `int` | that value, or `nil`. To read a variable **in a given solution**, use `value_of` |

**`solution`**

| Attribute | Type | Meaning |
|---|---|---|
| `exists` | `bool` | false if the problem has no solution, or the search was interrupted first |
| `values` | `map<string, int>` | every int and bool variable, by name |

---

## Creating a problem

Casting a string to `problem` creates a new, empty one.

```gaml
problem p <- problem("my_problem");
```

`problem(string name, string engine)` creates one solved by a named engine, among `choco`, `choco_lcg`, `lp` and `highs`. Which to pick is discussed in the [introduction](#what-the-engines-are-and-when-to-use-which); what each accepts is listed below.

## Engines

Everything in this reference works on every engine unless its documentation says otherwise. The operators that only the constraint engine can honour carry the sentence *Only available with the 'choco' engine*, and refuse the others at the point of use.

| | `choco`, `choco_lcg` | `lp`, `highs` |
|---|---|---|
| int, bool and continuous variables | yes | yes |
| set variables | yes | no |
| arithmetic and relational expressions | yes | linear ones only |
| `arithm`, `scalar` | yes | yes |
| global constraints (`all_different`, `circuit`, `table`, `knapsack`, …) | yes | no |
| `member`, `not_member` | yes | no |
| derived variables (`sum_var`, `min_var`, `element_var`, `abs_var`, …) | yes | no |
| combinators and `reify` | yes | no |
| `!=` in a posted constraint | yes | no |
| `search`, `minimize`, `maximize`, `optimize` | yes | yes |
| `all_solutions` | yes | no |
| hints, `reset`, search strategies | yes | no |
| `read_mps` | yes, if the file is integral | yes |
| `nodes`, `fails`, `solutions`, `search_time` | reported | left at zero |

The derived variables are the one place where the distinction is not obvious. `sum_var(vars)` creates a variable and ties it to its operands through a constraint posted in Choco, which a linear engine never sees, so the variable would be left free and the answer quietly wrong. They are refused rather than accepted and mis-solved; write the sum as an expression instead, as in `a + b + c`.

A time budget is honoured by `choco`, `choco_lcg` and `highs`. The `lp` engine cannot stop itself: its branch and bound runs to the end whatever budget it is given, and it writes the relaxation of each node to the standard output with no way to silence it. It is bundled inside Choco as an internal helper rather than as a solver meant to be exposed, and is kept here because it needs nothing to run.

Both linear engines take int, bool and continuous variables. The `lp` engine works in standard form, where every variable is shifted to be non-negative, so a variable with no lower bound is refused; `highs` takes the bounds as they are, infinite ones included.

### Shipping HiGHS

HiGHS is a native solver. Its binaries live in the plugin under `native/<os>/<arch>/`, are copied to a temporary directory on first use and loaded from there by absolute path, so nothing has to be added to the `PATH` of the machine nor to `java.library.path`, which the platform reads once at startup and never again.

Loading is attempted once. When it fails, the engine reports why, distinguishing a missing binary from one that cannot be loaded, instead of letting a link error surface in the middle of a simulation. A model can then fall back to another engine.

Binaries are currently shipped for Windows on x86_64 only. Adding a platform means dropping the shared library under `native/<os>/<arch>/` and declaring it in `Bundle-NativeCode`; nothing in the code changes.

The file has to be a shared library, not a static archive: `highs.dll` on Windows, `libhighs.so` on Linux, `libhighs.dylib` on macOS. A `.a` or a `.lib` is meant for a compiler and is inert once the program runs, so it cannot be loaded whatever it is named. HiGHS builds a static archive by default; a shared one comes from a release built as such, or from a build configured with `-DBUILD_SHARED_LIBS=ON`. The engine names this case when it reports why it could not load.

Such a build turns every HiGHS target into a shared library, including `highs_extras`, from which the main one resolves three symbols. When it produces a separate `libhighs_extras.so` alongside `libhighs.so`, drop both in the folder: the loader loads that one first, and ignores it when it is absent, as it is in a build that keeps the code inside the main library.

## Declaring variables

| Operator | Returns | |
|---|---|---|
| `int_var(problem, string, float lb, float ub)` | `pb_variable` | domain `[lb, ub]` |
| `int_var(problem, string, list<int>)` | `pb_variable` | explicit domain |
| `int_vars(problem, string, int n, float lb, float ub)` | `list<pb_variable>` | `n` variables named `<prefix>_0` … |
| `bool_var(problem, string)` | `pb_variable` | domain `[0, 1]` |
| `bool_vars(problem, string, int n)` | `list<pb_variable>` | |
| `real_var(problem, string, float lb, float ub)` | `pb_variable` | continuous |
| `set_var(problem, string, list<int> mandatory, list<int> possible)` | `pb_variable` | set variable |
| `variable_named(problem, string)` | `pb_variable` | look-up by name, `nil` if absent |

Bounds are floats, so `#infinity` and `-#infinity` are accepted and clamped to the range Choco supports (±21 474 836, since it caps domains at `Integer.MAX_VALUE / 100` to keep a margin against overflow inside the propagators). Beyond 65 536 values, a domain is represented by its bounds only rather than by an enumeration: much less memory, slightly weaker propagation. That is what you want for totals and objectives.

## Derived variables

Each of these declares a new variable and links it to its operands. Named with a `_var` suffix because `sum`, `min`, `max`, `count`, `abs` and `mod` are already operators of the GAML core.

| Operator | Returns |
|---|---|
| `sum_var(list<pb_variable>)` | the sum |
| `min_var(list<pb_variable>)` / `max_var(list<pb_variable>)` | the smallest / largest value taken |
| `count_var(list<pb_variable>, int value)` | how many variables take `value` |
| `arg_min_var(list<pb_variable>)` / `arg_max_var(list<pb_variable>)` | index of the smallest / largest |
| `element_var(list<int> table, pb_variable index)` | `table[index]`, indices from 0 |
| `element_var(matrix<int> table, int row, pb_variable index)` | the value read on that row, at a variable column |
| `mod_var(pb_variable, int divisor)` | the remainder |
| `abs_var(pb_variable)` | absolute value (a view, so it is free) |
| `neg_var(pb_variable)` | opposite (a view) |
| `offset_var(pb_variable, int)` | `x + k` (a view) |
| `scale_var(pb_variable, int)` | `x * k` (a view) |

Views cost neither a propagator nor a search decision: prefer them when they apply.

## Expressions

The arithmetic and relational operators of GAML are overloaded over `pb_variable`.

| Operator | Operands | Returns |
|---|---|---|
| `+` `-` `*` `/` `mod` | variable/variable, variable/int, int/variable | `pb_variable` |
| `-` | variable | `pb_variable` |
| `^` | variable/int, variable/variable | `pb_variable` |
| `=` `!=` `<` `<=` `>` `>=` | variable/variable, variable/int, int/variable | `constraint` |
| `same(pb_variable, pb_variable)` | | `bool` |
| `as_table(constraint)` | | `constraint` |

```gaml
do post(starts[before] + durations[before] <= starts[after]);
do post(quantity * unit_price = cost);
do post(queens[i] - queens[j] != j - i);
```

An arithmetic operator builds a tree and adds nothing to the problem. The tree is handed to Choco as a whole when the relation is posted, which lets it compile the expression rather than materialise one intermediate variable per operator. A `pb_variable` holding an expression reports `"expression"` as its `kind`, and is materialised on first use where a real variable is required, for instance as the objective of a search.

`=` builds a constraint here, not a boolean. `same` performs the identity test that `=` performs on every other type.

`as_table` recompiles a constraint built from an expression into a single table listing the combinations that satisfy it. A table propagates far more strongly than the decomposition, since it reasons over the whole relation at once, but the number of combinations grows as the product of the domain sizes.

Products, quotients, powers and remainders of two variables are non-linear constraints. The constraint engine propagates them natively, on the bounds: Ibex, the library Choco delegates to and which this plugin does not ship, is only needed for continuous non-linear relations, not for these. A linear engine refuses them, naming the sub-expression it cannot represent.

## Constraints

All of them return a `constraint`, which has to be posted to take effect.

### Arithmetic and membership

| Operator | Meaning |
|---|---|
| `arithm(pb_variable, string op, int)` | `x op v`, with `op` in `= != < <= > >=` |
| `arithm(pb_variable, string op, pb_variable)` | `x op y` |
| `arithm(pb_variable, string op1, pb_variable, string op2, int)` | `x op1 y op2 v`, `op1` in `+ - * /` |
| `scalar(list<pb_variable>, list<int> coeffs, string op, int)` | weighted sum compared to a constant |
| `scalar(list<pb_variable>, list<int> coeffs, string op, pb_variable)` | weighted sum compared to a variable |
| `member(pb_variable, list<int>)` | takes one of these values |
| `not_member(pb_variable, list<int>)` | takes none of them |
| `table(list<pb_variable>, matrix<int> rows)` | the values form one of the rows of the matrix |
| `table(list<pb_variable>, matrix<int> rows, bool allowed)` | one of the rows, or none of them when `allowed` is false |

### Over a list of variables

| Operator | Meaning |
|---|---|
| `all_different(list<pb_variable>)` | pairwise distinct |
| `all_different_except_0(list<pb_variable>)` | pairwise distinct, 0 meaning "unassigned" |
| `all_equal(list<pb_variable>)` / `not_all_equal(list<pb_variable>)` | |
| `element(pb_variable value, list<int> table, pb_variable index)` | `value = table[index]` |
| `among_values(pb_variable nb, list<pb_variable>, list<int> values)` | `nb` variables take one of `values` |
| `n_values(list<pb_variable>, pb_variable nb)` | exactly `nb` distinct values |
| `at_least_n_values` / `at_most_n_values(list<pb_variable>, pb_variable nb)` | bounds on that count |
| `global_cardinality(list<pb_variable>, list<int> values, list<pb_variable> occurrences, bool closed)` | occurrence count per value |
| `increasing(list<pb_variable>, int delta)` / `decreasing(…)` | monotone; `delta = 1` makes it strict |
| `sorted(list<pb_variable>, list<pb_variable>)` | the second list is the first, sorted |
| `lex_less` / `lex_less_eq(list<pb_variable>, list<pb_variable>)` | lexicographic order, useful to break symmetries |
| `inverse_channeling(list<pb_variable>, list<pb_variable>)` | `a[j] = i` iff `b[i] = j` |

A `table` is how a relation with no analytical form is expressed: a tabulated response curve, an empirical compatibility table, a rule set given by extension. It has one column per variable and one row per allowed combination, and it propagates over the whole relation at once. Its size grows as the product of the domains.

### Reals

Choco delegates every non-linear operation over reals to Ibex, a native library that is not shipped with the plugin, so only the constraints implemented in Java are exposed. Non-linear continuous relations are out of reach; the usual way around it is to work in fixed point, multiplying the quantities by a power of ten and modelling them as integers, which also keeps the full strength of the integer propagators.

| Operator | Returns | |
|---|---|---|
| `real_var(problem, string, float lb, float ub)` | `pb_variable` | declares a continuous variable |
| `real_scalar(list<pb_variable>, list<float> coeffs, string op, float value)` | `constraint` | weighted sum, over integer and real variables mixed |
| `real_element(pb_variable, list<float> table, pb_variable index)` | `constraint` | the real equals `table[index]` |
| `real_view(pb_variable, float precision)` | `pb_variable` | a real view of an integer variable, free |
| `set_precision(problem, float)` | `problem` | below which a real domain counts as instantiated |

`=` between a real variable and an integer one builds the channelling constraint between them rather than an arithmetic equality.

### Routing and packing

| Operator | Meaning |
|---|---|
| `circuit(list<pb_variable>)` | successors forming a single hamiltonian circuit |
| `sub_circuit(list<pb_variable>, pb_variable size)` | a circuit over `size` nodes, the others self-looping |
| `path(list<pb_variable>, pb_variable start, pb_variable end)` | a hamiltonian path |
| `tree(list<pb_variable>, pb_variable nb_roots)` | predecessors forming an anti-arborescence |
| `bin_packing(list<pb_variable> item_bin, list<int> item_size, list<pb_variable> bin_load, int offset)` | |
| `knapsack(list<pb_variable> occurrences, pb_variable weight_sum, pb_variable energy_sum, list<int> weights, list<int> energies)` | |

### Combining constraints

These take **unposted** constraints and produce one that can be posted.

| Operator | Returns | Meaning |
|---|---|---|
| `and_all(list<constraint>)` | `constraint` | all of them hold |
| `or_all(list<constraint>)` | `constraint` | at least one holds |
| `opposite(constraint)` | `constraint` | it does not hold (`not` is taken by the core) |
| `if_then(constraint, constraint)` | `constraint` | implication |
| `reify(constraint)` | `pb_variable` | a bool variable, true iff the constraint holds |

## Posting

| Operator | Returns |
|---|---|
| `post(constraint)` | the constraint |
| `post(problem, constraint)` | the constraint, checking it belongs to that problem |
| `post_all(list<constraint>)` | the list |

Posting is a side effect, so these are called through `do`:

```gaml
do post(all_different(queens));
```

Posting the same constraint twice is a no-op, so a constraint held in a variable can safely be posted inside a loop.

## Searching

| Operator | Returns |
|---|---|
| `search(problem)` | first solution found |
| `search(problem, float within)` | same, with a time budget |
| `minimize(problem, pb_variable objective)` | proven optimum |
| `minimize(problem, pb_variable objective, float within)` | best found within the budget |
| `maximize(problem, pb_variable objective)` | |
| `maximize(problem, pb_variable objective, float within)` | |
| `all_solutions(problem)` | `list<solution>`, every solution |
| `all_solutions(problem, int limit)` | `list<solution>`, at most `limit` |

`within` is a duration, so it is written in model time units: `5 #s`, `200 #ms`.

```gaml
solution best <- minimize(p, total_cost, 20 #s);
```

## Warm starting and resetting

| Operator | Returns | |
|---|---|---|
| `hint_from(problem, solution)` | `int` | replays a previous solution as hints; returns how many could be applied |
| `add_hint(pb_variable, int)` | `pb_variable` | a single hint |
| `clear_hints(problem)` | `problem` | forget every hint |
| `reset(problem)` | `problem` | bring the solver back to its initial state |

A hint tells the search which value to try first for a variable. It **guides** the search without restricting it: hints can make a search much faster or slightly slower, but they can never make a result wrong or hide a solution. 

`hint_from` matches variables **by name**, so the solution may come from a different `problem` object, typically the one built at the previous simulation step. That is what makes it usable with the rebuild-every-step pattern below. Variables of the solution with no counterpart in the target problem, and values that no longer belong to the domain, are skipped; the returned count is there to let you check that the match worked.

## Tuning the search

None of the operators below changes which solutions a problem has. They change the order in which the solver looks for them, and the cost of each node.

| Operator | Returns | |
|---|---|---|
| `use_strategy(problem, string name)` | `problem` | how to branch, over every integer variable |
| `use_strategy(problem, string name, list<pb_variable>)` | `problem` | the same, over a chosen subset |
| `with_last_conflict(problem)` | `problem` | retry the variable involved in the last failure |
| `with_conflict_ordering(problem)` | `problem` | same idea, over the whole recent conflict history |
| `with_best_bound(problem)` | `problem` | try the bound that looks best for the objective |
| `use_restarts(problem, string policy, int cutoff)` | `problem` | restart the search periodically |
| `record_nogoods(problem)` | `problem` | remember across restarts what has been proven impossible |
| `problem(string name, string engine)` | `problem` | create a problem solved by a named engine |

### Branching strategies

| Name | |
|---|---|
| `default` | what the solver uses when nothing is set |
| `input_order_lb` / `input_order_ub` | declaration order, smallest or largest value first |
| `min_dom_lb` / `min_dom_ub` | smallest domain first |
| `random` | random variable and value, seeded from the simulation random generator |
| `dom_over_w_deg` / `dom_over_w_deg_ref` | weighted degree, the classic adaptive heuristic |
| `activity_based` | branches on the variables the propagation touches most |
| `conflict_history` | weights variables by their recent involvement in failures |
| `failure_rate` / `failure_length` | two other failure-driven heuristics |
| `pick_on_dom` | |
| `round_robin` / `adaptive_round_robin` | alternate between several of the above |

### Restricting the branching

The three-operand form of `use_strategy` branches only on the variables given. By default the solver branches on every integer variable of the problem, including the ones produced by `sum_var`, `min_var` and the like, and the objective.

```gaml
do use_strategy(p, "dom_over_w_deg_ref", decisions);
```

The remaining variables are handled by a default strategy appended behind, through `makeCompleteStrategy`, so the search stays complete.

### Restarts

`cutoff` is the number of failures before the first restart. The policy decides how that number grows afterwards: `luby` follows the Luby sequence, `geometric` multiplies it by 1.2 each time, `linear` adds the cutoff, `constant` keeps it fixed, and `on_solution` ignores the cutoff and restarts after each solution.

`record_nogoods` makes each restart record the assignments the previous one has ruled out. The store is consulted at every node and is never reduced.

### Lazy clause generation

`problem(name, "choco_lcg")` enables LCG: the solver derives a clause from each conflict and keeps it, instead of only backtracking.

Three constraints on its use. It is decided at creation, since variables and propagators are built differently. It only covers constraints whose propagators can explain their deductions, and posting an unsupported one raises an error. And it encodes domains into boolean literals, so its cost grows with domain size rather than with the number of variables.

## Reading a problem from a file

| Operator | Returns | |
|---|---|---|
| `read_mps(string path)` | `problem` | reads an MPS file, to be solved by the linear engine |
| `read_mps(string path, string engine)` | `problem` | the same, for a named engine |
| `objective_of(problem)` | `string` | the objective the file declares, written out |
| `maximises(problem)` | `bool` | whether that objective has to be maximised |
| `optimize(problem)` | `solution` | solves for the objective the file carries, in its declared direction |
| `objective_value(solution)` | `float` | the value that objective takes in a solution |

MPS is the interchange format of linear and mixed integer programming. The sections read are NAME, OBJSENSE, ROWS, COLUMNS with its INTORG and INTEND markers, RHS, RANGES, BOUNDS and ENDATA, in both the fixed and the free layout. Variables, bounds, integrality, constraints and objective all come from the file; nothing is declared in the model.

The objective of such a file is a linear form over the columns rather than a column of its own, so it is not returned as a `pb_variable`: `optimize` uses it directly and `objective_value` evaluates it against a solution.

The path is resolved relative to the model, as everywhere else in GAML. Compressed files are not read: uncompress them first, whether they use gzip or the packed form that Netlib distributes for its own test set, which is a different format produced by its `emps` utility.

## Reading a solution

| Operator | Returns |
|---|---|
| `value_of(solution, pb_variable)` | `int`, or `nil` if no solution was found |
| `values_of(solution, list<pb_variable>)` | `list<int>`, in the same order |
| `set_value_of(solution, pb_variable)` | `list<int>`, the elements of a set variable |
| `real_value_of(solution, pb_variable)` | `float`, the value without rounding, for the continuous variables a linear engine carries |

---

## Semantics worth knowing

**A problem is mutable and shared.** It carries a propagation engine and a backtracking trail, so assigning it to another GAML variable shares it rather than copying it, and a simulation holding a live problem cannot be serialised. Build a problem, search it, drop it.

**The solver keeps its state between two searches.** Searching the same problem twice resumes where the previous search stopped, which is what makes an anytime search spread over several simulation cycles possible, but also means a second `search(p)` will not return the first solution again. Use `reset(p)` to start over, or build a fresh problem.

**Re-solving every N steps.** The usual pattern in a simulation is to solve the same system of constraints again and again, with data that has changed in between. Constants are copied into the constraints when they are built (`scalar(vars, coeffs, "<=", 15)` captured `coeffs` and `15` for good), so changing the data means rebuilding the problem. That is almost always the right choice: building a few dozen variables and a hundred propagators is negligeable compared to the time spent in search.

What is worth saving is not the construction but the search, by seeding it with the previous solution:

```gaml
solution previous <- nil;

reflex replan when: every(10 #cycles) {
    problem p <- build_problem();            // rebuilt with the current data
    if (previous != nil) { do hint_from(p, previous); }
    previous <- minimize(p, cost, 2 #s);
}
```

When the data moves little from one step to the next, the previous solution is nearly feasible and the solver reaches a good incumbent within the first few nodes instead of wandering. When it moves a lot, the hints are simply poor advice and cost a little time, never correctness.

**Every search is interruptible.** A stop criterion bound to the interruption of the simulation is installed for the duration of each search, so stopping or closing an experiment stops the solver. Prefer the forms with a time budget anyway: an unsatisfiable problem can otherwise keep the solver busy for a very long time.

**The number of variables is decided at runtime.** Nothing has to be declared in advance: variables are built in ordinary GAML loops, which is what makes "one decision variable per agent" natural.

```gaml
list<pb_variable> slot <- [];
loop w over: workers { slot <- slot + int_var(p, "slot_" + w.name, 1, nb_slots); }
do post(all_different(slot));

solution best <- minimize(p, sum_var(costs), 5 #s);
loop i from: 0 to: length(worker) - 1 {
    worker[i].assigned_slot <- value_of(best, slot[i]);
}
```

---

## Not exposed yet

- `cumulative` and the scheduling constraints that need Choco's `Task` type
- the set and graph constraint families (77 constraints), which need set and graph decision variables
- non-linear constraints over reals, which in Choco go through the native Ibex library
- `regular` / `costRegular`, which need automata
- a plain `sum` constraint: use `arithm(sum_var(vars), op, value)` or `scalar` with unit coefficients
- streaming enumeration: `all_solutions` materialises the whole list, there is no per-solution callback
- large neighborhood search (`setLNS`) and the alternative traversals (`setLDS`, `setDDS`, `setHBFS`)
- branching strategies for set, real and graph variables, only the integer ones are exposed

---

## Example models

In `models/Constraint Programming/`:

| Model | Archetype |
|---|---|
| `Cryptarithm.gaml` | pure satisfaction, SEND + MORE = MONEY |
| `N-Queens.gaml` | placement, shown on a grid |
| `Knapsack.gaml` | selection under a budget |
| `Graph Colouring.gaml` | partitioning, with symmetry breaking, drawn as a coloured graph |
| `Scheduling.gaml` | sequencing over time, minimising the makespan |
| `Travelling Salesman.gaml` | routing, with the tour drawn between scattered cities |
| `Task Assignment.gaml` | assignment over agents, with write-back into their attributes |
| `Livestock Feeding.gaml` | a full linear model translated from Choco Java |
| `Production Planning.gaml` | a linear model, run on either engine by changing one word |
| `MPS File.gaml` | a problem read from a file rather than declared |
| `Engine Benchmark.gaml` | the same problems solved by every engine, charted as they grow |
| `Non Linear.gaml` | a product of two decisions, and squares, on the constraint engine |
