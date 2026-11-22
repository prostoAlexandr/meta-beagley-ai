SUMMARY = "Kernel modifications for BeagleY-AI Labs"

# Добавляем локальный каталог files/ в начало пути поиска
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Добавляем наш конфигурационный фрагмент и патч в список источников
SRC_URI:append = " \
    file://enable-extra-options.cfg \
"