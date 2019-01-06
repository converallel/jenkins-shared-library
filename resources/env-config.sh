#!/usr/bin/env bash

# enable app_local.php
sed -i -e "s|//Configure::load('app_local', 'default')|Configure::load('app_local', 'default')|g" config/bootstrap.php

# write to app_local.php
cat > config/app_local.php <<EOF
<?php

return [
    'debug' => true,
    'Datasources' => [
        'default' => [
            'className' => 'Cake\Database\Connection',
            'driver' => 'Cake\Database\Driver\Mysql',
            'persistent' => false,
            'host' => '${DB_HOST}',
            'username' => '${DB_USERNAME}',
            'password' => '${DB_PASSWORD}',
            'database' => '${DB_NAME}',
            'timezone' => 'UTC',
            'cacheMetadata' => true,
            'quoteIdentifiers' => false,
            'log' => false,
        ],
        'test' => [
            'className' => 'Cake\Database\Connection',
            'driver' => 'Cake\Database\Driver\Mysql',
            'persistent' => false,
            'host' => '${DB_HOST}',
            'username' => '${DB_USERNAME}',
            'password' => '${DB_PASSWORD}',
            'database' => 'test_${DB_NAME}',
            'timezone' => 'UTC',
            'cacheMetadata' => true,
            'quoteIdentifiers' => false,
            'log' => false,
        ],
    ]
];
EOF
