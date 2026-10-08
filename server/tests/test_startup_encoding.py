"""Guards the bug that stopped the whole backend from starting on Windows.

`app/services/disease_ai/disease_model.py` printed a check-mark emoji at import
time. On Windows, whenever stdout is not a UTF-8 terminal — redirected to a log
file, piped, or running as a service — Python encodes stdout as cp1252 and that
print raises UnicodeEncodeError *during app import*, so uvicorn dies before it
can bind its port. The API looked "implemented" while the server was simply not
running, which is exactly what made the Android app show no live data.

These tests fail if any module-level print/log statement reintroduces a
character that cp1252 cannot encode.
"""

import pathlib

import pytest

APP_DIR = pathlib.Path(__file__).resolve().parent.parent / "app"


def _python_files():
    return sorted(APP_DIR.rglob("*.py"))


def test_app_package_exists():
    assert _python_files(), f"no python files found under {APP_DIR}"


@pytest.mark.parametrize("path", _python_files(), ids=lambda p: p.name)
def test_print_and_log_lines_survive_a_cp1252_console(path: pathlib.Path):
    """Any line that writes to stdout/stderr must be encodable as cp1252."""
    offenders = []
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if "print(" not in line and "logger" not in line:
            continue
        try:
            line.encode("cp1252")
        except UnicodeEncodeError:
            unencodable = sorted({c for c in line if not _cp1252_ok(c)})
            offenders.append((number, "".join(unencodable).encode("unicode_escape").decode()))

    assert not offenders, (
        f"{path.relative_to(APP_DIR.parent)} has console output that crashes on a "
        f"cp1252 console (line, chars): {offenders}. Use plain ASCII such as [OK]."
    )


def _cp1252_ok(char: str) -> bool:
    try:
        char.encode("cp1252")
    except UnicodeEncodeError:
        return False
    return True
