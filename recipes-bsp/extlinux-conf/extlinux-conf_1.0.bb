# Deploy-only file. File consumed via IMAGE_BOOT_FILES in michine configuration

SUMMARY = "U-Boot extlinux.conf for BeagleY-AI"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://extlinux.conf"
S = "${WORKDIR}"

# The file is board-specific (DTB name, root device)
PACKAGE_ARCH = "${MACHINE_ARCH}"

inherit deploy

do_deploy() {
    install -m 0644 ${WORKDIR}/extlinux.conf ${DEPLOYDIR}/extlinux.conf
}
addtask deploy after do_compile before do_build
