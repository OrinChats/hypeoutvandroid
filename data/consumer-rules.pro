# Regras de consumo do módulo `data`.
#
# Nada a declarar: o OkHttp 5 e o Coil publicam as próprias regras, e as classes de
# `data` são alcançadas por código (nunca por reflexão ou por nome em XML). O R8
# pode encolher o módulo inteiro no APK de release — é o que faz o APK sair com
# 2,1 MB em vez de 8 MB.
#
# O arquivo existe porque `consumerProguardFiles("consumer-rules.pro")` no
# `build.gradle.kts` exige que ele exista (o build falha sem, o que é o
# comportamento correto do AGP).
