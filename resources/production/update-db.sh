#!/usr/bin/env bash

bin/cake schema_cache clear
bin/cake migrations migrate --no-lock