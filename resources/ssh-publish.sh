#!/usr/bin/env bash

# create remote directory structure on jenkins server
mkdir -p ${SSH_REMOTE_DIR}
rsync -avRzhhe --delete bin/ composer.json composer.lock config/ .htaccess index.php plugins/ src/ webroot/ ${SSH_REMOTE_DIR}

# send files to jenkins home folder on the remote server
rsync -avRzhhe ssh --delete ${SSH_REMOTE_DIR} jenkins@${SERVER_NAME}:/home/jenkins

# execute commands on the remote server
ssh jenkins@${SERVER_NAME} << EOF

cd ${SSH_REMOTE_DIR}

# turn debug mode off
sed -i -e "s|'debug' => true|'debug' => false|g" config/app_local.php

# install dependencies
echo 'Y' | composer install --no-dev
composer dumpautoload -o
bin/cake plugin assets symlink

# update database
echo ${DB_PASSWORD} | mysql -h ${DB_HOST} -u ${DB_USERNAME} -p -e "CREATE DATABASE IF NOT EXISTS ${DB_NAME};"
bin/cake migrations migrate --no-lock
cd ~

# change owner to apache
sudo chown -R apache:apache ${GIT_REPO_NAME}

# send folder to document root
sudo rsync -avRzhhe --delete ${SSH_REMOTE_DIR} /var/www/html/

# remove the temporary repository
sudo rm -rf ${GIT_REPO_NAME}

EOF
