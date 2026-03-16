# Stella type checker

By Lev Sorvin (@metametamoon)

## Build instructions
Assuming the current directory is this repository's root:

```bash
./gradlew assembleDist
unzip -o build/distributions/stella-typechecker-1.0.zip
./stella-typechecker-1.0/bin/stella-typechecker --help
```

## Example run

```bash
cat tests/core/unexpected-lambda-bad.stella | ./stella-typechecker-1.0/bin/stella-typechecker
```

Possible output:
```
e [ERROR_UNEXPECTED_LAMBDA]
at file:///tmp/stella-stdin-16715331244192773291.stella:16:10:
found a lambda where type Nat was expected
- while checking type of Abstraction (16:10-16:34) is Nat
- while inferring type of Program (13:1-17:2)
```