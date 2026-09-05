# Image de production du back-end ESIC Connect.
#
# Build multi-étapes : compilation Maven dans une image jetable, puis
# copie du seul jar exécutable dans une image d'exécution minimale (JRE
# seul, pas le JDK complet). Les images Eclipse Temurin sont publiées en
# multi-architecture (amd64 ET arm64) : ce Dockerfile fonctionne à
# l'identique sur un poste de développement et sur une Raspberry Pi 4/5,
# sans configuration particulière ni émulation.

# --- Étape 1 : compilation ---------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Couche de dépendances mise en cache séparément du code source : un
# changement de code seul ne re-télécharge pas tout Maven Central.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -o clean package -DskipTests

# --- Étape 2 : exécution -------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Utilisateur non privilégié : l'application ne tourne jamais en root.
RUN groupadd --system esic && useradd --system --gid esic --no-create-home esic

COPY --from=build /build/target/*.jar /app/app.jar
RUN chown esic:esic /app/app.jar
USER esic

EXPOSE 8080

# La configuration provient entièrement des variables d'environnement
# (profil Spring, secrets, hôtes MySQL/Redis) — rien n'est codé en dur
# dans l'image, conformément à docs/11-guide-deploiement.md.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
