# Origine du code

Le point de depart est `Code Files/application-one-master/`, extrait de
`9781803236346_Code.zip`, lui-meme inclus dans l'archive fournie `9781803236346.zip`.

Support : Packt, *Containerize Spring Boot CRUD App with Docker and Docker Compose*,
ISBN 9781803236346, 2022.
Depot indique dans le support :
https://github.com/PacktPublishing/Containerize-springboot-CRUD-App-with-Docker-and-DockerCompose

Conserves : package `com.mycompany.applicationone`, classe de demarrage,
structure Maven, test de contexte Spring et route `/api/v1/welcome`.

Adaptations pour le TP : Spring Boot 2.5.5 / Java 11 vers 3.5.16 / Java 21,
controleur Visit-Counter, Redis a la place de l'exemple de base de donnees MongoDB
du cours, image multi-stage Alpine non-root, Compose, Nginx, tests et pipelines.

Les videos et les autres exemples du support ne sont pas redistribues.
Le PDF fourni sert de reference de presentation. Ses captures et les resultats
de ses auteurs ne sont pas presentes comme ceux de ce projet.
