#!/usr/bin/env bash
set -euo pipefail
java -version
python --version
gradle --version
sdkmanager --list_installed | sed -n '1,120p'

