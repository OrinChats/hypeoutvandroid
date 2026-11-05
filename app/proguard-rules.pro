# R8: o aplicativo não usa reflexão própria. As bibliotecas (Media3, OkHttp,
# Coil, Kotlin) trazem as regras de que precisam nos próprios artefatos.

-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Nomes de classe em pilha de erro ficam legíveis no relatório do R8.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Coroutines: o Media3 e o Coil chamam serviços internos por nome de classe.
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

# OkHttp arrasta estes opcionais quando o BouncyCastle não está presente.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Media3 referencia APIs que só existem nas versões novas do Android.
-dontwarn androidx.media3.decoder.**
-dontwarn androidx.media3.extractor.**

# O JSON do Android é do sistema; a única coisa que usamos é a API pública.
-dontwarn org.json.**
