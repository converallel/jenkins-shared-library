#!/usr/bin/env bash

LAST_UPDATED_BRANCH_LIST_FILE=${REPO_LOCAL_DIR}/branches/last_updated_branch_list

# write current branch names to a file
git ls-remote --heads https://${JENKINS_GIT_USERNAME}:${JENKINS_GIT_PASSWORD}@${GIT_REPO_URL} | cut -d/ -f3 | sort > branch_list

# if the last updated branch list file exists
if [[ -f ${LAST_UPDATED_BRANCH_LIST_FILE} ]]; then
    DELETED_BRANCH_LIST=()

    DROP_TABLE_COMMAND=""
    # for branches exist in the last updated branch list but not in the current branch list
    for BRANCH in $(comm -13 branch_list ${LAST_UPDATED_BRANCH_LIST_FILE}); do
        DELETED_BRANCH_LIST+=(${BRANCH})
        DB_NAME=$(echo "${GIT_REPO_NAME}_${BRANCH}" | sed 's/[^A-Za-z0-9_]/_/g')
        DROP_TABLE_COMMAND+="DROP DATABASE IF EXISTS ${DB_NAME};"
    done

    # if deleted branch list is not empty, remove the corresponding folders on staging server
    if [[ ${#DELETED_BRANCH_LIST[@]} -gt 0 ]]; then
        FOLDERS=$(IFS=, ; echo "${DELETED_BRANCH_LIST[*]}")

        # if there are more than 1 folders to delete, use brace expansion
        if [[ ${#DELETED_BRANCH_LIST[@]} -gt 1 ]]; then
            FOLDERS={${FOLDERS}}
        fi

        # delete the folders and dbs on the staging server
        ssh ${SSH_USERNAME}@${SSH_SERVER} << EOF
sudo rm -rf /var/www/html/${GIT_REPO_NAME}/${FOLDERS}
echo ${DB_PASSWORD} | mysql -h ${DB_HOST} -u ${DB_USERNAME} -p -e '${DROP_TABLE_COMMAND}'
EOF
    fi
fi

# update the stored branch list file
mv branch_list ${LAST_UPDATED_BRANCH_LIST_FILE}