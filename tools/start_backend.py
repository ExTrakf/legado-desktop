#!/usr/bin/env python3
"""Legado Desktop backend - cross-platform start script (Windows / Linux / macOS).

Requires: Python 3.8+ and a JDK 17+ (JAVA_HOME or on PATH).

Usage:
    python tools/start_backend.py [--build] [backend args...]
      --build   force re-run installDist (default: only if the distribution is missing)

    Backend args are passed through, e.g.:
      --port 2323 --host 127.0.0.1
      --set-js-source-token <token>
      --api-smoke-test, --dao-smoke-test, ... (smoke entries, run-and-exit)

Env:
    LEGADO_DESKTOP_HOME          data dir (default ~/.legado-desktop)
    LEGADO_DESKTOP_ENABLE_JCEF   1 -> enable the JCEF webview engine
    JAVA_HOME                    JDK home (falls back to java on PATH)

It launches the backend exactly like the Gradle-generated launcher:
    java <jvm-args> -cp "<backend>/build/install/.../lib/*" io.legado.desktop.MainKt <args>
(no reliance on .bat/.sh launchers, so it works from any shell on any OS)
"""
import os
import shlex
import shutil
import subprocess
import sys
import pathlib

JVM_ARGS = [
    "--add-exports", "java.base/java.lang=ALL-UNNAMED",
    "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
    "--add-exports", "java.desktop/sun.java2d=ALL-UNNAMED",
]
MAIN_CLASS = "io.legado.desktop.MainKt"


def find_java() -> str:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        cand = pathlib.Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if cand.exists():
            return str(cand)
    found = shutil.which("java")
    if found:
        return found
    print("[start-backend] ERROR: java not found (set JAVA_HOME or add java to PATH)", file=sys.stderr)
    sys.exit(2)


def main() -> int:
    args = sys.argv[1:]
    if any(a in args for a in ("-h", "--help", "-help")):
        print(__doc__)
        return 0

    force_build = "--build" in args
    rest = [a for a in args if a != "--build"]

    root = pathlib.Path(__file__).resolve().parent.parent
    backend = root / "backend"
    lib_dir = backend / "build" / "install" / "legado-desktop-backend" / "lib"
    main_jar = lib_dir / "legado-desktop-backend-0.1.0.jar"

    if not os.environ.get("LEGADO_DESKTOP_HOME"):
        os.environ["LEGADO_DESKTOP_HOME"] = str(pathlib.Path.home() / ".legado-desktop")
    print(f"[start-backend] LEGADO_DESKTOP_HOME={os.environ['LEGADO_DESKTOP_HOME']}")

    if force_build or not main_jar.exists():
        print("[start-backend] running installDist ...")
        gradlew = backend / ("gradlew.bat" if os.name == "nt" else "gradlew")
        code = subprocess.run(
            [str(gradlew), "installDist", "--console=plain"],
            cwd=str(backend),
        ).returncode
        if code != 0:
            print("[start-backend] installDist failed", file=sys.stderr)
            return code

    java = find_java()
    # lib/* wildcard: the JVM expands it itself (no shell needed), works on all OSes
    classpath = os.path.join(str(lib_dir), "*")
    cmd = [java, *JVM_ARGS, "-cp", classpath, MAIN_CLASS, *rest]
    print("[start-backend] starting:", shlex.join(cmd))
    return subprocess.run(cmd, cwd=str(backend)).returncode


if __name__ == "__main__":
    sys.exit(main())
