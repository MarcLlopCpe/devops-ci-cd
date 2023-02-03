# Compte Rendu Module DevOps - Pierre Jovetic/Marc LLOP

# TP1 DEVOPS : DOCKER

On commence par créer un dossier par services (et donc conteneur) nécessaire au fonctionnement de l'application :

* app_db : pour la base de données
* app_api : pour l'api
* app_http : Pour le serveur web

Commandes utiles :   
   
Pour voir la liste des dockers qui sont en train de tourner :
`sudo docker ps`   
Pour supprimer complètement un docker :
`sudo docker rm -f <NOM_DOCKER>`   
Pour accéder à un docker déjà lancé :
`sudo docker exec -it <NOM_DOCKER> /bin/sh`    
Pour voir les logs d'un docker :
`sudo docker logs (-f) <NOM_DOCKER>`      
Pour voir les statistiques d'un dokcer :
`sudo docker stats <NOM_DOCKER>`    
Pour voir les docker qui appartiennent à un réseau :
`sudo docker inspect network <NOM_NETWORK>`


## Q 1-1 Document your database container essentials: commands and Dockerfile.

Tout d'abord on va créer le network dans lequel va se trouver nos deux dockers (postgres et adminer) :
```shell
docker network create app-network
```
### Initialisation de la base de données
On crée un Dockerfile pour la création du docker de la base de données PostgreSQL :
```shell
FROM postgres:14.1-alpine 

ENV POSTGRES_DB=db \
	POSTGRES_USER=usr \
	POSTGRES_PASSWORD=pwd

COPY 01-CreateScheme.sql /docker-entrypoint-initdb.d
COPY 02-InsertData.sql /docker-entrypoint-initdb.d
```
Dans ce Dockerfile on va utiliser une image postgres avec la version 14.1-alpine. On renseigne également les varaibles d'environnement de la db.   
Pour que les tables et les données soient automatiquement intégrée à la bdd, on copie les scripts SQL dans le dossier ```/docker-entrypoint-initdb.d```. Ce dossier est automatiquement exécuter par le docker Postgres à son inititialisation.

On build ensuite le Dockerfile pour avoir une image custom :
```shell
docker build -t pierre/db .
```
On lance ensuite le docker, en indiquant le réseau dans lequel il se trouve :
```shell
docker run -d --net=app-network --name app-db pierre/db
```

### Visualisation de la base de données
On créer aussi un docker `adminer` pour pouvoir visualiser ce qu'il y a dans la bdd :
```shell
# On pull l'image
docker pull adminer
```
```shell
docker run -p "8090:8080" --net=app-network --name=adminer -d adminer
# On indique encore une fois le réseau dans lequel il sera pour qu'il puisse communiquer avec le docker que l'on a crée précédemment
```
On pourra donc visualiser la bdd depuis l'adresse `http://localhost:8090`  

### Persistance des données

Voici un exemple appliqué à un serveur d'argument pour assurer la persistence de ses données.
Par défaut si un chemin relatif `/var/lib/docker/volumes/`.

```sh
-v app-data:/var/lib/postgresql/data -d marc.llop/database 
# - v chemin/volume/local:/chemin/conteneur
```

 sudo docker run --name app-database -p 5432:5432 --net app-network -e POSTGRES_PASSWORD="toto" 


### Changement du mot de passe par défaut pour plus de "sécurité"
sudo docker exec -it app-database /bin/sh

psql -d db -U usr -h 127.0.0.1 -w toto -p 5432

## Q 1-2 Why do we need a multistage build? And explain each step of this dockerfile.

Dans notre cas on veut séparer le build et le run. Il nous faut donc un multistage build qui fera :
- dans un premier temps la partie build de l'application java
- dans un second temps la partie run de l'application java

```shell
# Build
FROM maven:3.8.6-amazoncorretto-17 AS myapp-build
ENV MYAPP_HOME /opt/myapp
WORKDIR $MYAPP_HOME
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests
```
Ce premier stage permet d'utiliser un docker maven pour compiler l'application java. Pour cela, maven a besoin d'avoir accès au `pom.xml` qui est copié dans le docker. On va enfin run la commande `mvn package -DskipTests` qui permet de construire le package de l'application pour qu'il puisse être exécuter dans le docker de run. L'options `-DskipTests` permet de skip les test effectué par maven lors du build d'une application.  

```shell
# Run
FROM amazoncorretto:17
ENV MYAPP_HOME /opt/myapp
WORKDIR $MYAPP_HOME
COPY --from=myapp-build $MYAPP_HOME/target/*.jar $MYAPP_HOME/myapp.jar

ENTRYPOINT java -jar myapp.jar
```
Le second stage permet d'exécuter l'application java en récupérant le package produit lors du premier stage (myapp-build).  
Ce stage va créer un docker avec l'image amazoncorretto:17 qui est une distribution jdk. On va récupérer le package créé dans le docker précédent et on va le copier à l'intérieur du docker de run pour ensuite l'exécuter.

Séparer ces deux étapes permet de bien faire la différence entre le build et le run de l'application. Cela rend le Dockerfile plus visible et maintenable. On pourra également réutilisé ces dokcers pour build d'autres applications.

## Q 1-3 Document docker-compose most important commands.
Build le docker compose :
```shell
docker-compose build 
```
Lancer le docker compose défini dans le ficher docker-compose.yml :
```shell
docker-compose up -d
```
Arrêter le docker compose :
```shell
docker-compose down
```
Voir les logs du docker compose :
```shell
docker-compose logs
```

## Q 1-4 Document your docker-compose file.

Fichier `docker-compose.yml` : 
```shell
version: '3.3'
services:
  backend:
    container_name: backend
    build: ./simple-api
    networks:
      - app-network
    depends_on:
      - database

  database:
    container_name: database
    restart: always
    build: ./database
    networks:
      - app-network
    env_file:
      - database/.env

  httpd:
    container_name: reverse_proxy
    build: ./httpd
    ports:
      - "80:80"
    networks:
      - app-network



volumes:
  my_db_volume:
    driver: local

networks:
  app-network:
```

Ce fichier permet de lancer tous les dockers que l'on a crée précedemment avec une seule commande.  

Le docker-compose va créer 3 dockers :
- `backend` : docker pour l'API qui est construit à partir de `./simple-api`. Il est connecté au réseau `app-network` et est dépendant de `database`.  
- `database` : docker pour la base de données qui est construit à partir de `./database`. Il est connecté au réseau `app-network`. On définit les variables d'environnemt qui sont dans le fichier .env et on déclare le volume qui sert pour la persistance des données.  
- `reverse-proxy` : docker pour le serveur web qui est construit à partir de `./httpd`. Il est connecté au réseau `app-network` et redirige son port 80 vers le port 80 de l'hôte.  

Grâce à `depends-on`, on peut préciser que certains docker sont dépendant des autres.   
On peut préciser aussi un network pour la création de nos dockers grâce à `networks`.   

## Q 1-5 Document your publication commands and published images in dockerhub.

Publier les images sur dockerhub permet de partager les images que l'on a crée. Celles-ci pourront être utilisé par d'autres utilisateurs sur d'autres machines.  
Pour cela il faut se connecter sur dockerhub :
```shell
docker login
```
Ensuite on tag l'image que l'on veut publier :  
```shell
docker tag devops-tp2_httpd USERNAME/devops-tp2_httpd:1.0
docker tag devops-tp2_backend USERNAME/devops-tp2_backend:1.0
docker tag devops-tp2_database USERNAME/devops-tp2_database:1.0
# docker tag <NOM-IMAGE> <USERNAME>/<NOM-IMAGE>:<VERSION>
```

Et enfin on push les images sur dockerhub :  
```shell
docker push USERNAME/devops-tp2_httpd
docker push USERNAME/devops-tp2_backend
docker push USERNAME/devops-tp2_database 
```

# TP2 DEVOPS : GITHUB ACTIONS

Dans ce TP nous allons voir comment utiliser github actions pour faire un pipeline de test et livraison de notre application.  
Dans un premier temps, le workflow testera le backend avec `mvn clean verify` puis avec `sonar` pour ensuite livrer automatiquement les images dans dockerhub.   
La commande `mvn clean verify` permet de à maven de compiler tous les modules et de vérifier si tous les tests d'intégration ont réussi.
Il exécutera à la fois des tests unitaires et des tests d'intégration.
Les tests unitaires permettent de tester les modules individuellement tandis que les tests d'intégration permet de tester les modules ensemble en tant que groupe.

## Q 2-1 What are testcontainers?

Les testcontainers sont des conteneurs mis en place par une bibliothèque java dans le but de faciliter les tests d'intégrations. Cela permet de tester l'application dans un environnement proche de celui de production sans avoir à configurer manuellement les environnements.

## Q 2-2 Document your Github Actions configurations.
Pour délivrer l'image des dockers sur dockerhub, on va utiliser un workflow github actions :
```
name: CI devops 2023
on:
  #to begin you want to launch this job in main and develop
  push:
    branches: main
  pull_request:

jobs:

  test-backend:
    runs-on: ubuntu-22.04
    steps:
      #checkout your github code using actions/checkout@v2.5.0
      - uses: actions/checkout@v2.5.0

      #do the same with another action (actions/setup-java@v3) that enable to setup jdk 17
      - name: Setup java
        uses: actions/setup-java@v3
        with:
          java-version: 17
          distribution: corretto

      #finally build your app with the latest command
      - name: Build and test with Maven
        run: mvn clean verify --file simpleapi

  # define job to build and publish docker image
  build-and-push-docker-image:
    needs: test-backend
    # run only when code is compiling and tests are passing
    runs-on: ubuntu-22.04

    # steps to perform in job
    steps:
      - name: Checkout code
        uses: actions/checkout@v2.5.0

      - name: Login to DockerHub
        run: docker login -u ${{ secrets.USERNAME }} -p ${{ secrets.TOKEN }}

      - name: Build image and push backend
        uses: docker/build-push-action@v3
        with:
          # relative path to the place where source code with Dockerfile is located
          context: ./simpleapi
          # Note: tags has to be all lower-case
          tags: ${{ secrets.USERNAME }}/tp_devops:simpleapi
          # build on feature branches, push only on main branch
          push: ${{ github.ref == 'refs/heads/main' }}

      - name: Build image and push database
        uses: docker/build-push-action@v3
        with:
          # relative path to the place where source code with Dockerfile is located
          context: ./app_db
          # Note: tags has to be all lower-case
          tags: ${{ secrets.USERNAME }}/tp_devops:app_db 
          # build on feature branches, push only on main branch
          push: ${{ github.ref == 'refs/heads/main' }}

      - name: Build image and push httpd
        uses: docker/build-push-action@v3
        with:
          # relative path to the place where source code with Dockerfile is located
          context: ./app_http
          # Note: tags has to be all lower-case
          tags: ${{ secrets.USERNAME }}/tp_devops:app_http
          # build on feature branches, push only on main branch
          push: ${{ github.ref == 'refs/heads/main' }}
```

Ce workflow sera déclenché lorsque qu'il y aura un push sur la branche main ou un pull.   
Au déclenchement ce worklow va exécuter 2 jobs :
* test-backend :
Ce job vérifie dans un premier temps le code GitHub à l'aide de l'action "actions/checkout". Ensuite il configure Java 17 et construit et teste l'application avec Maven.

* build-and-push_docker_image :

Ce deuxième job va s'occuper de publier les imagess sur dockerhub. Pour que ce job s'exécute, il faut que le test-backend soit réussi. Tout d'abord, le job vérifie le code et se log à Dockerhub à l'aide des varaibles secrets github renseignées préalablement. Enfin, il va build et push 3 images sur dockerhub : backend, database et httpd

## Q 2-3 Document your Quality Gate configuration.  
Pour la Quality Gate on utilise un service de vérification de code externe qui est Sonar Cloud. Ce service va lire le code d'un dépôt github et les résultats fournis par maven (par exemeple le coverage) dans le but d'analyser le code produit et détecter d'éventuels dysfonctionnements ou erreurs au sein du code.

Pour ceci on va rajouter une étape dans le `main.yml` au niveau du premier job `test-backend`:  
```
- name: Sonar Test
        run: mvn -B verify sonar:sonar -Dsonar.projectKey=PierreJovetic_DEVOPS-TP2 -Dsonar.organization=pierrejovetic -Dsonar.host.url=https://sonarcloud.io -Dsonar.login=${{ secrets.SONAR_TOKEN }}  --file ./simpleapi/pom.xml
```
Au préalable, on crée un project sur Sonar Cloud et on récupère une clé de projet et d'organisation que l'on renseigne dans la commande. On ajoute également un TOKEN Sonar dans les variables secrètes de gitlab.  
A l'exécution du workflow, lorsque cette étape sera terminée, on pourra voir les résultats de l'analyse directement sur l'interface web de Sonar Cloud

# TP3 DEVOPS : ANSIBLE

## Prérequis 

Avant de commencer toute manipulation, on va vérifier si l'accès au serveur distant est OK.  
On essaye donc de se connecter en ssh avec la clé fournie :
```
# ssh -i <ID_RSA_PATH> <USER>@<IP or DNS> 
ssh -i ~/id_rsa centos@marc.llop.takima.cloud
```

On teste ensuite l'accès au serveur avec ansible :
* On ajoute l'hôte dans le fichier `/etc/ansible/hosts` --> `centos@marc.llop.takima.cloud`   
* On test un ping vers le serveur avec ansible :
```
ansible all -m ping --private-key=~/id_rsa -u centos
```
* On installe apache :
```
# on ajoute l'option become pour avoir les droits d'installation
ansible all -m yum -a "name=httpd state=present" --private-key=~/id_rsa -u centos --become
```
Maintenant que l'on a vérifié l'accès au serveur via ssh et via ansible et qu'on a installé apache, on peut commencer à configurer ansible pour faire le déploiement des dockers sur la machine distante.  

## Q 3-1 Document your inventory and base commands.

On va créer un ficher d'inventaire dans Ansible qui permet de définir les hôtes gérés par Ansible :
```
devops-ci-cd/ansible/setup.yml
```
```
all:
  vars:
    ansible_user: centos
    ansible_ssh_private_key_file: ~/id_rsa
  children:
    prod:
      hosts: marc.llop.takima.cloud
```
Dans ce fichier on renseigne le user, la clé ssh et l'adresse de l'hôte.   
On peut maintenant tester si l'inventaire fonctionne correctement en faisant un ping vers le serveur en utilisant le ficher setup.yml.  
```
ansible -i inventories/setup.yml -m ping
```
On peut aussi supprimer apache en faisant :
```
ansible -i inventories/setup.yml -m yum -a "name=httpd state=absent" --become
```

## Q 3-2 Document your playbook.

Notre premier playbook consiste à faire un ping sur le serveur.   
Pour cela on crée le fichier `devops-ci-cd/ansible/playbook.yml`
```
-hosts: all
 gather_facts: false
 become: yes
 
 tasks:
   - name: Test connection
     ping:
```
Ce playbook effectue la tâche "Test connection" qui lance un ping vers l'host présent dans l'inventories.

On exécute le playbook en faisant : 
```
ansible-playbook -i inventories/setup.yml playbook.yml
```

Notre deuxième playbook permettra d'installer docker sur le serveur.   
On modifie donc le fichier playbook.yml :
```
- hosts: all
  gather_facts: false
  become: yes

# Install Docker
  tasks:
  - name: Clean packages
    command:
      cmd: dnf clean -y packages

  - name: Install device-mapper-persistent-data
    dnf:
      name: device-mapper-persistent-data
      state: latest

  - name: Install lvm2
    dnf:
      name: lvm2
      state: latest

  - name: add repo docker
    command:
      cmd: sudo dnf config-manager --add-repo=https://download.docker.com/linux/centos/docker-ce.repo

  - name: Install Docker
    dnf:
      name: docker-ce
      state: present

  - name: install python3
    dnf:
      name: python3

  - name: Pip install
    pip:
      name: docker

  - name: Make sure Docker is running
    service: name=docker state=started
    tags: docker
```
Le playbook effectue plusieurs tâches d'installation des différent service qui permettent l'utilisation de docker. Il faut bien penser à mettre become à "yes" car il nous faut les droits pour pouvoir procéder à l'installation.   

une bonne pratique consiste à utiliser différents rôles pour différentes tâches dans le playbook.   
On va donc créer des rôles pour chaque étape du déploiement sur notre serveur. On va commencer par le rôle de l'installation de docker. On peut créer l'arborescence de notre rôle automatiquement en faisant :   
```
ansible-galaxy init roles/docker
```
On se servira seulement du répertoire `tasks`.   
On modifie le `main.yml` du répertoire `tasks` en indiquant les tâches à effectuer :
```
# tasks file for roles/docker
- name: Clean packages
    command:
      cmd: dnf clean -y packages

  - name: Install device-mapper-persistent-data
    dnf:
      name: device-mapper-persistent-data
      state: latest

  - name: Install lvm2
    dnf:
      name: lvm2
      state: latest

  - name: add repo docker
    command:
      cmd: sudo dnf config-manager --add-repo=https://download.docker.com/linux/centos/docker-ce.repo

  - name: Install Docker
    dnf:
      name: docker-ce
      state: present

  - name: install python3
    dnf:
      name: python3

  - name: Pip install
    pip:
      name: docker

  - name: Make sure Docker is running
    service: name=docker state=started
    tags: docker
```

Dans le playbook on aura donc seulement :
``` 
- hosts: all
  gather_facts: false
  become: yes
  roles: 
    - docker

```
On lance le playbook pour installer docker sur notre machine distante :
```
ansible all -i inventories/setup.yml playbook.yml
```
## Q 3-3 Document your docker_container tasks configuration.

On va déployer la totalité de l'application avec les rôles. Pour ceci on commence par créer tous les rôles dont nous avons besoin :   
```
ansible-galaxy init roles/docker
ansible-galaxy init roles/network
ansible-galaxy init roles/database
ansible-galaxy init roles/proxy
ansible-galaxy init roles/app
```
* role docker : installer docker sur la machine distante 
```
# /roles/dokcer/main.yml
# tasks file for roles/docker
- name: Clean packages
    command:
      cmd: dnf clean -y packages

  - name: Install device-mapper-persistent-data
    dnf:
      name: device-mapper-persistent-data
      state: latest

  - name: Install lvm2
    dnf:
      name: lvm2
      state: latest

  - name: add repo docker
    command:
      cmd: sudo dnf config-manager --add-repo=https://download.docker.com/linux/centos/docker-ce.repo

  - name: Install Docker
    dnf:
      name: docker-ce
      state: present

  - name: install python3
    dnf:
      name: python3

  - name: Pip install
    pip:
      name: docker

  - name: Make sure Docker is running
    service: name=docker state=started
    tags: docker
```
* role network : création du réseau pour les dockers

```
# /roles/network/main.yml
# tasks file for roles/network
- name: Create a network
  community.docker.docker_network:
    name: app-network
```
* role database : lancement du dokcer pour la database
```
# /roles/database/main.yml
# tasks file for roles/database
- name: Launching database
  docker_container:
    name: database
    restart_policy: always
    state: started
    image: marcllopcpe/database:latest
    networks:
      - name: "app-network"
    env:
      POSTGRES_PASSWORD: "pwd"
      POSTGRES_USER: "usr"
      POSTGRES_DB: "db"
```
* role proxy : lancement du docker httpd
```
# /roles/proxy/main.yml
# tasks file for roles/proxy
- name: Launching proxy
  docker_container:
    name: httpd
    state: started
    image: marcllopcpe/httpd:latest
    published_ports:
      - "80:80"
      - "8080:8080"
    networks:
      - name: "app-network"
```
* role app : lancement du docker pour le backend et le frontend
```
# /roles/app/main.yml
# tasks file for roles/app
- name: Launching frontend
  docker_container:
    name: front
    state: started
    image: marcllopcpe/front:latest
    networks:
      - name: "app-network"

- name: Launching backend
  docker_container:
    name: backend
    state: started
    image: marcllopcpe/simple-api:latest
    networks:
      - name: "app-network"
```

On modifie ensuite le playbook : 
```
- hosts: all
  gather_facts: false
  become: yes
  roles: 
    - docker
    - network
    - database
    - proxy
    - app
```

Notre application est désormais déployée sur le serveur :
* `marc.llop.takima.cloud/` permet d'accéder au frontend de l'application
* `marc.llop.takima.cloud:8080/students/` permet d'accéder au backend de l'application
