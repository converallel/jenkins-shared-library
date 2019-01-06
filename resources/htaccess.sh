#!/usr/bin/env bash

cat > .htaccess <<EOF
<IfModule mod_rewrite.c>
    RewriteEngine On
    RewriteBase   /${SSH_REMOTE_DIR}
    RewriteRule   ^(\.well-known/.*)$ \$1 [L]
    RewriteRule   ^$   webroot/    [L]
    RewriteRule   (.*) webroot/\$1    [L]
</IfModule>
EOF

cat > webroot/.htaccess <<EOF
<IfModule mod_rewrite.c>
    RewriteEngine On
    RewriteBase   /${SSH_REMOTE_DIR}/webroot
    RewriteCond   %{REQUEST_FILENAME} !-f
    RewriteCond   %{REQUEST_URI} !^/(webroot/)?(img|css|js)/(.*)$
    RewriteRule   ^ index.php [L]
</IfModule>
EOF