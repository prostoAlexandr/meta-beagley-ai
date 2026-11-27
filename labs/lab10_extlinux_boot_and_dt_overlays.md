In this lab we replace the default meta-ti GRUB EFI boot with **U-Boot extlinux**. This lets us apply **Device Tree overlays** (`.dtbo`) at boot time and edit the boot configuration directly on the board, as BeagleBoard.org images do.

---

# Lab No. 10: Replacing GRUB EFI with U-Boot extlinux and enabling DT overlays

### Objectives

1. Remove GRUB EFI from the `beagley-custom` machine.
2. Write an `extlinux.conf` with a boot menu and an overlay line.
3. Put the kernel, DTB and `extlinux.conf` on the FAT boot partition with a deploy-only recipe.
4. Mount the FAT partition in Linux at `/boot/firmware` and enable an overlay from the board.

---

### Theoretical introduction

* **Boot chain:** ROM → `tiboot3.bin` (R5 SPL) → `tispl.bin` (A53 SPL, TF-A, OP-TEE) → `u-boot.img` → `bootflow scan` → Linux. The bootloaders live on the first (FAT) SD card partition.
* **Where GRUB comes from:** `k3.inc` adds `efi` to `MACHINE_FEATURES`. This selects the `sdimage-2part-efi.wks.in` layout (GRUB on the FAT partition), and `packagegroup-core-boot` then installs `grub-efi`. Without `efi`, meta-ti uses `sdimage-2part.wks`, which only copies the files listed in `IMAGE_BOOT_FILES`.
* **Why overlays need extlinux:** the kernel receives **one** DTB, so the overlays must be merged into it before the kernel starts. U-Boot does this for extlinux (`FDTOVERLAYS`). The U-Boot EFI path does not (see `TODO: Apply extension overlay` in `boot/bootmeth_efi.c`). GRUB can only replace the whole DTB.
* **How an overlay applies:** the base DTB is compiled with `dtc -@` and contains `__symbols__` (label → path). The `.dtbo` contains `__fixups__`. `fdt_overlay_apply()` resolves the labels and merges the fragments into the base tree.
* **`do_install` vs `do_deploy`:** `do_install` files become packages in the **rootfs**. `do_deploy` files go to **`DEPLOY_DIR_IMAGE`**, and wic builds the FAT partition from there.

---

### Step 1. Remove EFI from the machine

In `conf/machine/beagley-custom.conf`:

```
# Boot via U-Boot extlinux instead of GRUB EFI
MACHINE_FEATURES:remove = "efi"
```

Check:

```
kas-container shell kas-project.yml -c "bitbake -e beagley-ai-image | grep -E '^(WKS_FILE|MACHINE_FEATURES)='"
```

The result must be `WKS_FILE="sdimage-2part.wks"`, with no `efi` in `MACHINE_FEATURES`.

---

### Step 2. Write `extlinux.conf`

`recipes-bsp/extlinux-conf/files/extlinux.conf`:

```
# U-Boot extlinux configuration for BeagleY-AI
# Paths are relative to /extlinux/ on FAT partition
MENU TITLE BeagleY-AI boot menu
TIMEOUT 30
DEFAULT beagley

LABEL beagley
    MENU LABEL BeagleY-AI (with overlays)
    KERNEL ../Image
    FDT ../dtb/ti/k3-am67a-beagley-ai.dtb
    APPEND root=/dev/mmcblk1p2 rootwait rw rootfstype=ext4 console=ttyS2,115200n8
    #FDTOVERLAYS ../dtb/ti/example.dtbo

LABEL safe
    MENU LABEL BeagleY-AI (base DTB, no overlays)
    KERNEL ../Image
    FDT ../dtb/ti/k3-am67a-beagley-ai.dtb
    APPEND root=/dev/mmcblk1p2 rootwait rw rootfstype=ext4 console=ttyS2,115200n8
```

* `TIMEOUT` is in tenths of a second. The menu appears only if the file has a `MENU` keyword; without it U-Boot boots `DEFAULT` immediately.
* `FDTOVERLAYS` is a space-separated list of `.dtbo` files.
* `safe` is the fallback entry for an overlay or an edit that breaks boot.

---

### Step 3. Deploy-only recipe

`recipes-bsp/extlinux-conf/extlinux-conf_1.0.bb`:

```
SUMMARY = "U-Boot extlinux.conf for BeagleY-AI"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://extlinux.conf"
S = "${WORKDIR}"
PACKAGE_ARCH = "${MACHINE_ARCH}"

inherit deploy

do_deploy() {
    install -m 0644 ${WORKDIR}/extlinux.conf ${DEPLOYDIR}/extlinux.conf
}
addtask deploy after do_compile before do_build
```

There is no `do_install` on purpose: the file goes to the FAT partition, not to the rootfs.

---

### Step 4. Put the boot files on the FAT partition

In `conf/machine/beagley-custom.conf`:

```
IMAGE_BOOT_FILES:append = " \
    Image \
    k3-am67a-beagley-ai.dtb;dtb/ti/k3-am67a-beagley-ai.dtb \
    extlinux.conf;extlinux/extlinux.conf \
"
do_image_wic[depends] += "extlinux-conf:do_deploy"
```

* `source;destination` maps a file in `DEPLOY_DIR_IMAGE` to a path on the FAT partition.
* The explicit `do_image_wic[depends]` is needed because `extlinux-conf` is not a package of the image.
* Add overlays one by one, without a `*.dtbo` glob: each overlay is deployed in three copies (with and without the version and machine suffixes).

Build, then check the FAT partition without the board:

```
kas-container build kas-project.yml
cd build/deploy-ti/images/beagley-custom
sudo mount -o loop,ro,offset=$((2048*512)) beagley-ai-image-beagley-custom.rootfs.wic /mnt
find /mnt && sudo umount /mnt
```

---

### Step 5. Flash and check the boot log

```
sudo bmaptool copy beagley-ai-image-beagley-custom.rootfs.wic.xz /dev/sdX
```

Expected UART output:

```
BeagleY-AI boot menu
1:      BeagleY-AI (with overlays)
2:      BeagleY-AI (base DTB, no overlays)
Retrieving file: /extlinux/../Image
Retrieving file: /extlinux/../dtb/ti/k3-am67a-beagley-ai.dtb
Starting kernel ...
```

### Why `extlinux.conf` cannot live in the rootfs

The first attempt installed it into the rootfs `/boot/extlinux/`, and U-Boot reported `0 bootflows`. Running `bootflow scan -lae` in the U-Boot console showed the reason: partition 2 was skipped with `err=-22`.

* **U-Boot bootstd scans only partitions with the bootable (active) flag**, if any exist. Only the FAT partition has it.
* Moving the flag to the rootfs made the **UART completely silent**, because the **ROM loads `tiboot3.bin` only from the active FAT partition**.

So the boot files must be on the FAT partition. This is also the layout BeagleBoard.org uses.

---

### Step 6. Mount the FAT partition at `/boot/firmware`

Override `fstab` for our machine. The existing `base-files_%.bbappend` already has `FILESEXTRAPATHS:prepend := "${THISDIR}/files:"`. BitBake looks in `files/<override>/` first, so a file in `files/beagley-custom/` is used only for this machine:

```
recipes-core/base-files/files/
├── beagley-distro/motd        # distro override
└── beagley-custom/fstab       # machine override
```

Copy `poky/meta/recipes-core/base-files/base-files/fstab` and add:

```
# FAT boot partition: bootloaders, kernel, DTBs and extlinux/extlinux.conf
/dev/mmcblk1p1       /boot/firmware       vfat       defaults,noatime,nofail  0  0
```

We use `/boot/firmware` instead of `/boot` so that the rootfs `/boot/dtb/ti/*.dtbo` (all overlays) stays visible. Check on the board with `findmnt /boot/firmware`.

---

### Step 7. Enable an overlay on the board

```
cp /boot/dtb/ti/k3-am67a-beagley-ai-spidev0.dtbo /boot/firmware/dtb/ti/
vi /boot/firmware/extlinux/extlinux.conf    # FDTOVERLAYS ../dtb/ti/k3-am67a-beagley-ai-spidev0.dtbo
sync && reboot
```

* If the overlay file is missing, U-Boot prints `Failed loading overlay` and boots without it.
* If the overlay fails to apply, or applies but describes the hardware wrongly, the boot may fail. Choose `safe` (`2`) in the menu.
* Edits on the board are lost on reflash. Copy the ones worth keeping into `recipes-bsp/extlinux-conf/files/extlinux.conf`.

---

🎯 The board now boots through U-Boot extlinux from the FAT partition, has a fallback menu entry, applies DT overlays from `FDTOVERLAYS`, and the boot configuration can be edited from Linux at `/boot/firmware`.
