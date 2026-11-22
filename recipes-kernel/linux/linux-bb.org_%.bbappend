SUMMARY = "Kernel modifications for BeagleY-AI Labs"

# Prepend the local files/ directory to the search path
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Add our kernel config fragment to the sources
SRC_URI:append = " \
    file://enable-extra-options.cfg \
"