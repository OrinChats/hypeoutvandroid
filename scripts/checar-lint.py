#!/usr/bin/env python3
"""Falha o build quando o relatório do lint tem erro de verdade.

O `lint { abortOnError = false }` do projeto existe porque o `assembleRelease`
não pode ser bloqueado por aviso de estilo. Em CI é o contrário: erro do lint —
`NewApi`, `MissingSuperCall`, recurso inexistente — é o tipo de coisa que compila
e derruba o aplicativo em API 21. Este script lê os relatórios XML e devolve
código 1 se houver qualquer `severity="Error"` ou `"Fatal"`.

Foi ele que teria pego `ConcurrentHashMap.newKeySet()` (API 24) num projeto com
`minSdk 21`.
"""

from __future__ import annotations

import glob
import sys
import xml.etree.ElementTree as ET

# Fatal = o APK não funciona; Error = quebra em alguma configuração.
FATAIS = {"Error", "Fatal"}


def main() -> int:
    relatorios = sorted(glob.glob("*/build/reports/lint-results-*.xml"))
    if not relatorios:
        print("nenhum relatório de lint encontrado — rode ./gradlew lintDebug antes")
        return 1

    erros = 0
    for caminho in relatorios:
        try:
            raiz = ET.parse(caminho).getroot()
        except ET.ParseError as falha:
            print(f"não consegui ler {caminho}: {falha}")
            return 1

        for problema in raiz.findall("issue"):
            severidade = problema.get("severity", "")
            if severidade not in FATAIS:
                continue
            erros += 1
            local = problema.find("location")
            onde = ""
            if local is not None:
                onde = f" {local.get('file', '?')}:{local.get('line', '?')}"
            print(f"[{severidade}] {problema.get('id')}: {problema.get('message')}{onde}")

    if erros:
        print(f"\n{erros} erro(s) de lint — o APK não deve ser publicado assim.")
        return 1

    print("lint sem erros (avisos de estilo não bloqueiam).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
