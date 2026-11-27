SUMMARY = "Kernel modifications for BeagleY-AI Labs"

# Prepend the local files/ directory to the search path
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Add our kernel config fragments to the sources
SRC_URI:append = " \
    file://enable-extra-options.cfg \
    file://disable-unused.cfg \
"

# meta-ti setup-defconfig.inc merges only the fragments listed here
KERNEL_CONFIG_FRAGMENTS:append = " \
    ${WORKDIR}/enable-extra-options.cfg \
    ${WORKDIR}/disable-unused.cfg \
"