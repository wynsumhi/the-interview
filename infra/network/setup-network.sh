#!/bin/bash
docker network inspect theinterview-net >/dev/null 2>&1 || \
    docker network create theinterview-net