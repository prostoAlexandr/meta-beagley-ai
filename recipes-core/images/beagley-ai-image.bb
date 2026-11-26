SUMMARY = "Custom minimal image for BeagleY-AI labs"
DESCRIPTION = "Minimal image based on core-image-minimal with dropbear, kernel modules and ninvaders included."
LICENSE = "MIT"

# Inherit all functionality of the standard minimal image
require recipes-core/images/core-image-minimal.bb

# Add the required packages directly in the image recipe
IMAGE_INSTALL:append = "\
    dropbear \
    openssh-sftp-server \
    kernel-modules \
    ninvaders \
"

IMAGE_FEATURES += " debug-tweaks"