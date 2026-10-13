# Part A - Docker tutorial study notes

Authors: Ali Dridi, Rayenne Abid, Aymen Chahed, Bochra Karoui.

Source: the supplied Packt archive, *Containerize Spring Boot CRUD App with
Docker and Docker Compose*, ISBN 9781803236346. These notes use its overview and
code examples. They are not a viewing certificate or an answer to an unavailable
graded quiz.

## 1. Images, containers and registries

An image is a packaged filesystem and runtime configuration. A container is an
isolated process created from that image. Containers share the host kernel;
they are not full virtual machines. Docker Desktop provides a Linux environment
for Linux containers on Windows. A registry such as Docker Hub stores images.
A tag names an image version; a digest identifies its exact content.

## 2. The development workflow

The tutorial starts with a Spring Boot REST endpoint. Maven builds an executable
JAR, a Dockerfile defines its runtime, `docker build` creates the image, and
`docker run` starts a container. `docker tag`, `docker push` and `docker pull`
connect local development to a registry. A host-to-container port mapping makes
the HTTP endpoint reachable from the browser.

## 3. Adapting the supplied example

The starting project is `application-one-master`: its original route is
`/api/v1/welcome`. The group keeps the package structure and startup class,
upgrades Java and Spring Boot, and replaces the greeting with a visit counter.
Although later tutorial examples use MongoDB, this assignment specifically
requires Redis and a key named `hits`. Every visit uses Redis INCR so concurrent
web replicas cannot lose updates through a read-modify-write race.

## 4. Building a smaller runtime

The build stage contains Maven and a JDK. The final stage contains only an
Alpine-based JRE and the application JAR. Copying `pom.xml` before `src` lets
Docker reuse the dependency layer when only source code changes. A non-root user
runs the service. Alpine does not automatically make an application secure;
dependencies and runtime images still require maintenance.

## 5. Networking, persistence and Compose

A user-defined bridge network provides service-name discovery. `localhost`
inside the web container refers to that container, not Redis, so the application
connects to `db-service`. Redis writes an append-only file in `/data`, mounted
from a named volume. Removing a container keeps that volume unless it is also
explicitly removed. Docker Compose defines the network, volume, services and
environment together. Nginx exposes one host port and distributes requests
among three web replicas.

## 6. Continuous integration

Every commit on `main` triggers a workflow. Compilation and unit tests must pass
before the image is published. Credentials are stored in the CI secret store.
Commit-based tags preserve traceability, while `latest` is a movable convenience
tag. A Docker Hub push and an anonymous pull are separate checks: a successful
build alone does not prove that an image is publicly available.
