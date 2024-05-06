# IB pseudocode transpiler
This is a transpiler for IB pseudocode to various languages (currently only Python), which includes a lexer and a
parser.

## Informal specification
The transpiler follows official documents as much as possible:
- [Approved notation for developing pseudocode](https://computersciencewiki.org/images/3/3e/Approved_notation_for_developing_pseudocode.pdf)
- [Pseudocode in examinations](https://computersciencewiki.org/images/c/c6/IB-Pseudocode-rules.pdf)

The far majority of valid IB pseudocode should transpile as intended. Exceptions or clarifications are listed below.

### Deviations and extensions
- The not-equals sign is `!=`, not `≠`.
- The division symbol is always `div`, not `/`.
- Strings can be multiline; there is no special multiline string syntax.
- The three defined string escape sequences are `\n`, `\t`, and `\\` for newlines, tabs, and backslashes respectively. If an escape is not in this list, it is interpreted as though the escape backslash were not there; e.g. `\a` and `a` are the same.
- Methods or functions are defined as follows. Return statements are optional.
```
method myMethod(PARAM1, PARAM2)
    // code
    return "my value"
end method
```

### Clarifications
The official specifications leave some behavior undefined, ambiguous, or vague. This section clarifies how the compiler
deals with these cases.

- Variables do not need to be and cannot be declared. All variables are global and dynamically typed.
- Comments have no bearing on the program's behavior.
- Classes and associated properties or methods exist internally, but there is currently no way to define or modify them.
- The `input` command will always treat the input as a string unless it can be parsed into an integer or float. The prompt displayed is always `Enter VARIABLE_NAME: ` where `VARIABLE_NAME` is the name of the variable.
- The `end` command does not need to specify what type of block it is ending; for example, both `end` and `end if` are acceptable.

**Identifier names**

- For all identifier names, underscores are allowed anywhere and numbers are allowed except in the first character.
- Variable names `[_A-Z][_\dA-Z]*` must be all caps.
- Method names `[_a-z][_\dA-Za-z]*` must begin with a lowercase letter or underscore and have at least one lowercase letter.
- Class names must begin with an uppercase letter but not be all caps.
- Identifiers having only underscores are always interpreted as variable names.

**Built-in classes**

The items in a given `Array`, `Collection`, `Stack`, or `Queue` do not all have to be the same type.
- `Boolean`
- `Int`
- `Float`
- `Array`
- `String`
- `Collection`
- `Stack`
- `Queue`