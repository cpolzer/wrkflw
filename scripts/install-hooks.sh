#!/bin/sh
set -e

REPO_ROOT=$(git rev-parse --show-toplevel)
HOOKS_SRC="$REPO_ROOT/hooks"
HOOKS_DST="$REPO_ROOT/.git/hooks"

write_hook() {
    src="$1"
    dst="$2"
    original="${dst}.wrkflw-original"

    {
        printf '#!/bin/sh\n'
        printf '## wrkflw-managed — do not edit by hand; re-run scripts/install-hooks.sh to regenerate\n'
        printf '# wrkflw validation (runs first, fail-fast)\n'
        printf 'sh "%s" "$@" || exit 1\n' "$src"
        if [ -f "$original" ]; then
            printf '# Original hook preserved below\n'
            cat "$original"
        fi
    } > "$dst"
    chmod +x "$dst"
}

install_hook() {
    name="$1"
    src="$HOOKS_SRC/$name"

    if [ ! -f "$src" ]; then
        echo "[install-hooks] WARN: $src not found, skipping"
        return
    fi

    dst="$HOOKS_DST/$name"
    original="${dst}.wrkflw-original"

    if [ -f "$dst" ] && ! grep -q "## wrkflw-managed" "$dst" 2>/dev/null; then
        echo "[install-hooks] Wrapping existing $name hook..."
        cp "$dst" "$original"
    else
        echo "[install-hooks] Installing $name hook..."
    fi

    write_hook "$src" "$dst"
}

install_hook "pre-commit"
install_hook "commit-msg"

echo "[install-hooks] Done. Git hooks installed in $HOOKS_DST"
