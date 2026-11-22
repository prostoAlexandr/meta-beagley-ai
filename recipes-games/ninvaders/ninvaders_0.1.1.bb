SUMMARY = "Space Invaders clone in ncurses"
DESCRIPTION = "nInvaders is a terminal-based game inspired by Space Invaders, written in C using the ncurses library."
HOMEPAGE = "https://ninvaders.sourceforge.net/"
SECTION = "console/games"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://nInvaders.c;md5=d3b22b9996b86202cdc58487fc6ad8e1"

SRC_URI = "${SOURCEFORGE_MIRROR}/ninvaders/ninvaders-${PV}.tar.gz \
           file://0001-Custom-title-for-BeagleY-AI.patch \
           file://0002-Custom-title-for-BeagleY-AI.patch \
           "
SRC_URI[sha256sum] = "bfbc5c378704d9cf5e7fed288dac88859149bee5ed0850175759d310b61fd30b"

DEPENDS = "ncurses" 
# Флаги компилятора для совместимости со стандартами C23 / GCC 10+ 
CFLAGS:append = " -std=gnu17 -fcommon" 

# Передаем кросс-компилятор и переменные BitBake в Makefile 
EXTRA_OEMAKE = 'CC="${CC}" CFLAGS="${CFLAGS}" LDFLAGS="${LDFLAGS}"' 

do_install() {
    install -d ${D}${bindir}
    install -m 0755 nInvaders ${D}${bindir}/ninvaders
}