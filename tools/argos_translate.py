#!/usr/bin/env python3
import argparse
import sys


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--from", dest="src", required=True)
    parser.add_argument("--to", dest="tgt", required=True)
    parser.add_argument("--text", dest="text", required=True)
    args = parser.parse_args()

    try:
        import argostranslate.package
        import argostranslate.translate
    except Exception:
        # argostranslate not installed
        return 2

    src = args.src.strip().lower()
    tgt = args.tgt.strip().lower()
    text = args.text

    try:
        installed = argostranslate.translate.get_installed_languages()

        def find_lang(code: str):
            for lang in installed:
                if lang.code == code:
                    return lang
            return None

        from_lang = find_lang(src)
        to_lang = find_lang(tgt)

        # Attempt one-time model install if missing pair
        if from_lang is None or to_lang is None or not any(t.code == tgt for t in from_lang.translations):
            available = argostranslate.package.get_available_packages()
            pkg = next((p for p in available if p.from_code == src and p.to_code == tgt), None)
            if pkg is not None:
                path = pkg.download()
                argostranslate.package.install_from_path(path)
                installed = argostranslate.translate.get_installed_languages()
                from_lang = find_lang(src)
                to_lang = find_lang(tgt)

        if from_lang is None or to_lang is None:
            return 3

        translation = from_lang.get_translation(to_lang)
        result = translation.translate(text)
        sys.stdout.write(result)
        return 0
    except Exception:
        return 4


if __name__ == "__main__":
    raise SystemExit(main())
