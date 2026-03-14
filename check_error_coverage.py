import sys
import os
import re

GROUPS = {
    "necessary": [
        "ERROR_MISSING_MAIN",
        "ERROR_UNDEFINED_VARIABLE",
        "ERROR_UNEXPECTED_TYPE_FOR_EXPRESSION",
        "ERROR_NOT_A_FUNCTION",
        "ERROR_NOT_A_TUPLE",
        "ERROR_NOT_A_RECORD",
        "ERROR_NOT_A_LIST",
        "ERROR_UNEXPECTED_LAMBDA",
        "ERROR_UNEXPECTED_TYPE_FOR_PARAMETER",
        "ERROR_UNEXPECTED_TUPLE",
        "ERROR_UNEXPECTED_RECORD",
        "ERROR_UNEXPECTED_VARIANT",
        "ERROR_UNEXPECTED_LIST",
        "ERROR_UNEXPECTED_INJECTION",
        "ERROR_MISSING_RECORD_FIELDS",
        "ERROR_UNEXPECTED_RECORD_FIELDS",
        "ERROR_UNEXPECTED_FIELD_ACCESS",
        "ERROR_UNEXPECTED_VARIANT_LABEL",
        "ERROR_TUPLE_INDEX_OUT_OF_BOUNDS",
        "ERROR_UNEXPECTED_TUPLE_LENGTH",
        "ERROR_AMBIGUOUS_SUM_TYPE",
        "ERROR_AMBIGUOUS_VARIANT_TYPE",
        "ERROR_AMBIGUOUS_LIST",
        "ERROR_ILLEGAL_EMPTY_MATCHING",
        "ERROR_NONEXHAUSTIVE_MATCH_PATTERNS",
        "ERROR_UNEXPECTED_PATTERN_FOR_TYPE",
        "ERROR_DUPLICATE_RECORD_FIELDS",
        "ERROR_DUPLICATE_RECORD_TYPE_FIELDS",
        "ERROR_DUPLICATE_VARIANT_TYPE_FIELDS",
        "ERROR_DUPLICATE_FUNCTION_DECLARATION"
    ],
}

TESTS_DIR = "tests"
MAX_FILES_VERBOSE = 4


def collect_test_files():
    result = []
    for root, _, files in os.walk(TESTS_DIR):
        for f in files:
            if f.endswith(".stella"):
                result.append(os.path.join(root, f))
    return result


def find_expected_code(path):
    with open(path) as f:
        content = f.read()
    m = re.search(r'"code"\s*:\s*"([^"]+)"', content)
    return m.group(1) if m else None


def main():
    verbose = "--verbose" in sys.argv
    test_files = collect_test_files()

    code_to_files = {}
    for path in test_files:
        code = find_expected_code(path)
        if code:
            code_to_files.setdefault(code, []).append(path)

    for group_name, codes in GROUPS.items():
        print(f"=== {group_name} ===")
        for code in codes:
            files = code_to_files.get(code, [])
            print(f"  {code}: {len(files)}")
            if verbose:
                for f in files[:MAX_FILES_VERBOSE]:
                    print(f"    - {f}")
                if len(files) > MAX_FILES_VERBOSE:
                    print(f"    ... and {len(files) - MAX_FILES_VERBOSE} more")


if __name__ == "__main__":
    main()
