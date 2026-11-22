SUMMARY = "Custom minimal image for BeagleY-AI labs"
DESCRIPTION = "Minimal image based on core-image-minimal with dropbear, kernel modules and ninvaders included."
LICENSE = "MIT"

# Наследуем всю функциональность стандартного минимального образа
require recipes-core/images/core-image-minimal.bb

# Добавляем нужные нам пакеты прямо в рецепт образа
IMAGE_INSTALL:append = "\
    dropbear \
    openssh-sftp-server \
    kernel-modules \
    ninvaders \
"