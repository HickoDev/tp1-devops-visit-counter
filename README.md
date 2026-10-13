# TP1 GRP1 - Visit-Counter

**Ali Dridi, Rayenne Abid, Aymen Chahed, Bochra Karoui**  
Laboratoire DevOps - Saloua Ben Yahia - INSAT - 2026/2027

Adaptation du code Spring Boot de l'archive fournie. Voir [SOURCE.md](SOURCE.md).
L'application incrémente la clé Redis `hits` et affiche le compteur et l'identifiant
du conteneur. La route historique `/api/v1/welcome` reste disponible.

## Démarrer

Prérequis : Docker Desktop avec le moteur Linux, Docker Compose et le port 80 libre.
Les ports sont définis dans `.env`, qui ne contient aucun secret.

```powershell
docker version
docker login
docker build -t counter-app:1.0 .
docker compose up -d --scale web=3
docker compose ps
```

Ouvrir http://localhost/ . Le seul port hôte est celui du proxy Nginx.
Les trois conteneurs `web` partagent Redis et restent accessibles uniquement sur
le réseau Docker. `/health` vérifie Redis sans incrémenter le compteur ;
`/api/visits` renvoie une visite en JSON pour les tests de répartition.

## Partie A : tutoriel en anglais

Voir [TUTORIAL_EN.md](TUTORIAL_EN.md). Le tutoriel Packt utilise Spring Boot et
MongoDB dans certains exemples. Le cahier des charges de ce TP impose Redis :
le contrôleur d'origine a donc été adapté. Aucune attestation de visionnage ou
note du tutoriel n'est produite par ce projet.

## Partie B : déploiement manuel

Effectuer ces commandes avant le déploiement Compose, lorsque `app-network`,
`redis-data`, `db-service` et `counter-web` n'existent pas encore.
Depuis le dossier de travail `TP_Docker_DRIDI_ABID_CHAHED_KAROUI/` :

```powershell
docker network create app-network
docker volume create redis-data
docker run -d --name db-service --network app-network -v redis-data:/data redis:7.4-alpine redis-server --appendonly yes --appendfsync always
docker run -d --name counter-web --network app-network -p 80:8080 -e REDIS_HOST=db-service counter-app:1.0
docker images counter-app:1.0
docker network inspect app-network
docker exec counter-web id
docker inspect db-service
```

Après plusieurs visites, relever la valeur puis supprimer Redis et le recréer :

```powershell
docker exec db-service redis-cli GET hits
docker rm -f db-service
docker run -d --name db-service --network app-network -v redis-data:/data redis:7.4-alpine redis-server --appendonly yes --appendfsync always
docker exec db-service redis-cli GET hits
```

La valeur doit être identique ; la visite suivante ajoute 1. Pendant la panne,
l'application renvoie HTTP 503. Le volume seul ne suffit pas : l'AOF écrit les
opérations sur disque. `appendfsync always` privilégie la durabilité de ce TP
par rapport au débit ; `everysec` peut perdre environ une seconde d'écritures.

Pour passer à Compose, retirer uniquement les deux conteneurs manuels et leur
réseau ; conserver le volume. Compose peut avertir qu'un volume créé à la main
existe déjà, puis le réutiliser.

```powershell
docker rm -f counter-web db-service
docker network rm app-network
docker compose config --quiet
docker compose up -d --scale web=3
```

## Tests et preuves

Le Dockerfile lance `mvn verify` avant de construire l'image finale. Les 7 tests
comprennent le chargement du contexte, l'incrément, le format de réponse,
l'ancienne route, les sondes et les erreurs Redis. Redis est simulé dans les
tests unitaires ; les tests d'intégration utilisent de vrais conteneurs.

Avec Java 21 et Maven disponibles :

```shell
mvn -B -ntp verify
```

Sur un environnement sans les ressources du TP, après le build :

```shell
python scripts/validate_lab.py
```

Ce script teste la panne Redis, la persistance, l'utilisateur non-root et
30 requêtes concurrentes via Nginx. Il refuse d'écraser une ressource existante.
Les journaux bruts et `results.json` sont dans `evidence/` dans l'archive livrée.
Les captures web sont réalisées par un test Chromium isolé. Les autres images
capturent une page locale affichant les sorties réellement enregistrées ; elles
ne sont pas des captures du terminal Windows. Les journaux restent vérifiables.

## Publication Docker Hub

Compte prévu : `hickodev100`. Le compte et la connexion doivent être validés
avant ces commandes. Le succès d'un push doit être vérifié avant d'annoncer
que l'image est publique.

```powershell
docker tag counter-app:1.0 hickodev100/counter-app:1.0
docker push hickodev100/counter-app:1.0
docker pull hickodev100/counter-app:1.0
```

Page du dépôt : https://hub.docker.com/r/hickodev100/counter-app

## Partie C : pipeline automatique

Dépôt : https://github.com/HickoDev/tp1-devops-visit-counter (privé).
Le workflow [ci.yml](.github/workflows/ci.yml) se déclenche sur chaque push vers
`main`, sur les pull requests et manuellement. Il compile et teste avec Maven,
archive les rapports JUnit, construit l'image, puis publie depuis `main` seulement.
Un échec de test empêche le job de publication. Les tags `sha-<commit>` et
`latest` sont publiés après validation.

Secrets GitHub nécessaires : `DOCKERHUB_USERNAME` et `DOCKERHUB_TOKEN` (token
Docker Hub avec droit d'écriture). Ils se configurent dans Settings > Secrets
and variables > Actions. Aucun secret ne doit être ajouté dans `.env` ou Git.
Un job de publication échoué pour secret absent ne signifie pas un pipeline
complet réussi : consulter le lien de l'exécution dans le rapport.

Un [Jenkinsfile](Jenkinsfile) équivalent est fourni si Jenkins est demandé.
Créer un projet Pipeline depuis SCM, avec un agent Linux disposant de Docker,
Java 21 et Maven, les plugins Pipeline, Git, GitHub, Credentials Binding et JUnit.
Enregistrer `dockerhub-credentials` (utilisateur + token). Configurer le webhook
GitHub vers `https://VOTRE-JENKINS/github-webhook/`, événement push. L'agent doit
pouvoir joindre Docker et Docker Hub. Le Jenkinsfile est une alternative fournie,
pas une exécution Jenkins prétendument réalisée.

## Nettoyage en une commande

```powershell
docker compose down --rmi all --volumes --remove-orphans
```

Cette commande supprime les conteneurs, le réseau, le volume déclaré (donc le
compteur) et les images des services du projet quand elles ne sont pas utilisées
ailleurs. Elle ne purge pas les caches de build ni les ressources d'autres TP.
Un `docker system prune -a --volumes` global n'arrête pas les conteneurs actifs
et n'est pas une commande de suppression ciblée du projet.

## Débogage et réflexion

```powershell
docker compose logs -f
docker compose exec web sh
docker inspect db-service
```

`RUN` exécute une commande pendant la construction et enregistre les changements
dans l'image. `CMD` définit la commande par défaut du conteneur ; elle est lancée
à l'exécution et peut être remplacée par les arguments de `docker run`.
`EXPOSE` documente un port mais ne le publie pas sur l'hôte. Plusieurs conteneurs
peuvent écouter leur propre port 8080 ; un seul processus peut occuper la même
adresse et le même port TCP de l'hôte. Nginx centralise donc l'accès sur le port 80.
