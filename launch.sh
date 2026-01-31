#!/bin/bash

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}Сборка проекта через Maven...${NC}"
mvn clean package

if [ $? -ne 0 ]; then
    echo -e "${RED}Ошибка сборки!${NC}"
    exit 1
fi

JAR_FILE=$(find linghy/target -maxdepth 1 -name "linghy-1.6.5.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}Jar-файл не найден!${NC}"
    exit 1
fi

echo -e "${GREEN}Запуск linghy launcher...${NC}"
java -jar "$JAR_FILE"
