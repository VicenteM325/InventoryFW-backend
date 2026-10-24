# Compilación con Temurin JDK 25
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Primero solo el wrapper de Maven y el pom: la capa con las dependencias
# descargadas se reutiliza entre builds mientras pom.xml no cambie.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B clean package -DskipTests

# Ejecución ligera con Temurin JRE 25
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Sin esto, la JVM usa UTC (zona horaria por defecto de la imagen base),
# así que todas las marcas de tiempo de negocio (fecha de venta, creación
# de alertas/notificaciones) quedarían 6 horas adelantadas respecto a la
# hora real de Guatemala, donde opera Multiservicios Wilson - visible por
# ejemplo en que "Ventas de hoy" del dashboard no contaría ventas hechas
# en la tarde/noche local.
#
# Memoria (Railway cobra por la RAM realmente usada):
# - Xmx512m fijo, no un porcentaje del contenedor, para que el consumo no
#   dependa del límite configurado en Railway (sin límite, la JVM vería la
#   memoria del host). En pruebas con 1 vCPU aguanta dos fotocopias de DPI
#   simultáneas a partir de fotos originales de 12 MP, o tres con fotos de
#   2560 px, con ~720 MiB de RSS como máximo; con 384m se queda sin heap.
# - SerialGC: con pocos núcleos procesa las imágenes más rápido que G1, y
#   así no depende de cuántas CPUs vea la JVM en el host.
# - ExitOnOutOfMemoryError: si aun así se agota el heap, el proceso termina
#   y Railway lo reinicia, en vez de quedar vivo pero sin poder responder.
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xmx512m", "-XX:+ExitOnOutOfMemoryError", "-Duser.timezone=America/Guatemala", "-jar", "app.jar"]
