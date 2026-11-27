In this lab we make the kernel config fragment of our layer actually apply, use it to change a visible option, and then trim the generic `bb.org_defconfig` of everything the BeagleY-AI does not have, to make the kernel and the image smaller.

---

# Lab No. 11: Kernel configuration with config fragments

### Objectives

1. Prove that `enable-extra-options.cfg` was never applied, and find out why.
2. Learn where to read the kernel configuration: in the build, on the board, in `menuconfig`.
3. Fix the bbappend with `KERNEL_CONFIG_FRAGMENTS` and enable a visible option (`CONFIG_LOCALVERSION`).
4. Find the drivers the board does not need and disable them with a second fragment.
5. Generate a fragment with `menuconfig` + `diffconfig`.
6. Measure the kernel and the image before and after.

---

### Theoretical introduction

* **Who creates `.config`:** the `do_configure` task. `linux-bb.org` does not use the one from `kernel.bbclass` but the one from `meta-ti/meta-ti-bsp/recipes-kernel/linux/setup-defconfig.inc`. It copies `defconfig` (one line: `use-kernel-config=bb.org_defconfig`) to `.config`, runs `make bb.org_defconfig`, and then calls `merge_config.sh` with the files listed in **`KERNEL_CONFIG_FRAGMENTS`**.
* **`SRC_URI` only fetches.** A `file://x.cfg` in `SRC_URI` is copied to `WORKDIR`, nothing more. Poky's `kernel-yocto` class merges `.cfg` files from `SRC_URI` automatically; meta-ti does not.
* **A fragment** is a small file of `CONFIG_X=y`, `CONFIG_X=m` or `# CONFIG_X is not set` lines. `merge_config.sh` applies the fragments in order, so a later fragment wins over an earlier one and over the defconfig.
* **Silent failures:** meta-ti calls `merge_config.sh -m`, which skips the "Value requested for CONFIG_X not in final .config" check. A line that Kconfig rejects (unmet dependency, `select`, no prompt) is dropped without a warning, so always check the final `.config`.
* **Release name:** `kernel.release` = version + `CONFIG_LOCALVERSION` + `KERNEL_LOCALVERSION` (`-ti`, set in `setup-defconfig.inc`).
* **Where to read the configuration:** `$K/git/arch/arm64/configs/bb.org_defconfig` (the start), `$K/build/.config` (the result), `/proc/config.gz` on the board (`CONFIG_IKCONFIG_PROC=y`), and `menuconfig` with `/` to see "Depends on", "Selected by" and the prompt of a symbol.

In the commands below:

```sh
K=build/tmp/work/beagley_custom-poky-linux/linux-bb.org/6.12.57+git
S=build/tmp/work-shared/beagley-custom/kernel-source
R=build/tmp/work/beagley_custom-poky-linux/beagley-ai-image/1.0/rootfs
D=build/deploy-ti/images/beagley-custom
```

---

### Step 1. Prove that the fragment is not applied

```sh
grep -n merge_config $K/temp/log.do_configure      # nothing: no fragment was ever merged
ls $K/enable-extra-options.cfg                     # but the file is in WORKDIR
grep -n 'KERNEL_CONFIG_FRAGMENTS\|merge_config' meta-ti/meta-ti-bsp/recipes-kernel/linux/setup-defconfig.inc
kas-container shell kas-project.yml -c "bitbake-getvar -r virtual/kernel KERNEL_CONFIG_FRAGMENTS"   # empty
```

On the board, `zcat /proc/config.gz | grep -E 'INPUT_POLLDEV|I2C_CHARDEV|DYNAMIC_DEBUG'` shows `I2C_CHARDEV=y` and `DYNAMIC_DEBUG=y` only because `bb.org_defconfig` already sets them. `INPUT_POLLDEV` no longer exists in 6.12.

---

### Step 2. Find what the board uses and what it does not

Record the baseline: `ls -lL $D/Image $D/modules-beagley-custom.tgz $D/*.rootfs.{tar.xz,wic,wic.xz}`, `du -sh` of `$R`, `$R/usr/lib/modules` and `$R/boot` (one `du` per directory: `du` counts nested directories only once), `uname -r` and `df -h /` on the board.

What is installed, largest first:

```sh
M=$R/usr/lib/modules/$(ls $R/usr/lib/modules)/kernel
find $M -name '*.ko*' -printf '%s %P\n' | sort -n | tail -30
for d in $(find $M -mindepth 2 -maxdepth 3 -type d); do echo "$(du -sk $d | cut -f1) $d"; done | sort -n | tail -40
grep -E '^CONFIG_[A-Z0-9_]+_FS=y' $K/build/.config        # file systems built into Image
```

What the board really uses (`lspci` and `lsusb` are not in the image, BusyBox `readlink` takes one file):

```sh
lsmod
cat /proc/device-tree/compatible
ls /sys/bus/pci/devices/; cat /sys/bus/usb/devices/*/product
for l in /sys/bus/*/devices/*/driver; do t=$(readlink $l); echo ${t##*/}; done | sort -u   # bound drivers
```

The bound drivers are only K3 / BeagleY-AI hardware (`am65-cpsw-nuss`, `TI DP83867`, `sdhci-am654`, `omap8250`, `cdns3-ti`, `j721e-pcie`, `tidss`, `it66121`, `cc33xx_sdio`, `pwm-fan`, `tps65219*`, ...). `/sys/bus/platform/drivers` also lists drivers of other SoCs (`hisi_*`, `acpi-*`, `max77620`, `smc91x`, `macb`, `sdhci-arasan`) that are built in but bound to nothing.

We decided option by option: remove what has no hardware on the board and is not planned, keep what we use or may plug in. Prefer disabling a whole menu (`CONFIG_WLAN_VENDOR_INTEL`) over single drivers.

---

### Step 3. Fix the bbappend and enable a visible option

`recipes-kernel/linux/linux-bb.org_%.bbappend`:

```
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI:append = " \
    file://enable-extra-options.cfg \
    file://disable-unused.cfg \
"

# meta-ti setup-defconfig.inc merges only the fragments listed here
KERNEL_CONFIG_FRAGMENTS:append = " \
    ${WORKDIR}/enable-extra-options.cfg \
    ${WORKDIR}/disable-unused.cfg \
"
```

`recipes-kernel/linux/files/enable-extra-options.cfg` (without the obsolete `INPUT_POLLDEV`):

```
CONFIG_I2C_CHARDEV=y
CONFIG_DYNAMIC_DEBUG=y
CONFIG_LOCALVERSION="-beagley"
```

The configuration takes seconds, so check it before compiling anything:

```sh
kas-container shell kas-project.yml -c "bitbake -c configure virtual/kernel"
grep -n Merging $K/temp/log.do_configure                 # both fragments
grep -E '^CONFIG_LOCALVERSION=' $K/build/.config
```

The new `kernel.release` also renames `/lib/modules/<release>`, so `Image` on FAT and the modules in the rootfs must come from the same build: flash the whole image.

---

### Step 4. Disable the unneeded drivers

What we applied is **`recipes-kernel/linux/files/disable-unused.cfg`**: 100 `# CONFIG_X is not set` lines in 19 commented groups. By decision we removed:

* **Hardware the board does not have:** AMD GPUs, Wi-Fi of all vendors except TI (CC33xx), Ethernet of all vendors except TI (CPSW), TV tuners, radio and SDR, raw NAND/NOR flash (MTD, UBIFS, JFFS2), SATA, UFS, NVDIMM, 802.15.4 and NFC, old pre-UVC USB webcams, server power supply monitoring (PMBus).
* **Built-in drivers of other SoCs and platforms:** ACPI, HiSilicon and CCI PMUs, the Tegra PMIC, the Arasan SD controller.
* **Features we do not use:** the `kheaders` module, Btrfs/XFS/F2FS, network and cluster file systems (SMB, Ceph, GFS2, AFS, DLM), the NFS server, RAID and device mapper, DRBD, staging drivers, ATM, Hyper-V, SCSI target, cluster and telecom protocols (SCTP, TIPC, RxRPC, batman-adv, Open vSwitch, IPVS), virtualization leftovers (VMware NIC, 9p, vgem), eCryptfs.

We kept CAN, IIO and HWMON (sensor HATs, `pwm-fan`), USB storage, USB serial, UVC and the IMX219 camera, HID, NVMe and PCI, netfilter, the NFS client, bonding, PPP and WireGuard.

Two lines are not obvious: `CONFIG_MEDIA_SUPPORT_FILTER=y` (without it the `MEDIA_*_SUPPORT` options have no prompt and stay on) and `# CONFIG_BLK_DEV_RBD is not set` (it `select`s `CEPH_LIB`).

Check that every line stuck. The loop must print nothing:

```sh
kas-container shell kas-project.yml -c "bitbake -c configure virtual/kernel"
F=recipes-kernel/linux/files/disable-unused.cfg
for o in $(sed -n 's/^# \(CONFIG_[A-Z0-9_]*\) is not set/\1/p' $F); do
    grep "^$o=" $K/build/.config && echo "  ^ still set"
done
grep -c '=y' $K/build/.config; grep -c '=m' $K/build/.config
```

---

### Step 5. Generate a fragment with `menuconfig` and `diffconfig`

`menuconfig` needs its own terminal. In the container BitBake falls back to a detached tmux session, so start tmux yourself in an interactive shell:

```sh
kas-container shell kas-project.yml
tmux
source /work/poky/oe-init-build-env /work/build      # the tmux shell has no BitBake environment
K=/work/build/tmp/work/beagley_custom-poky-linux/linux-bb.org/6.12.57+git
bitbake -c menuconfig virtual/kernel                 # opens in a new tmux pane
```

Change *General setup → Local version* to `-beagley-test`, exit and save. BitBake prints `Changed configuration saved ... Recompile will be forced`.

```sh
bitbake -c diffconfig virtual/kernel
cat $K/fragment.cfg                                  # CONFIG_LOCALVERSION="-beagley-test"
bitbake -c configure -f virtual/kernel               # throw the hand-edited .config away
```

`menuconfig` saves `.config.orig` first, and `diffconfig` writes the changed lines into `fragment.cfg` (`do_diffconfig` in `cml1.bbclass`). This is how to get the exact option names for a fragment. Without the forced `configure`, the next compile would use the hand-edited `.config`.

---

### Step 6. An option that cannot be disabled

Add `# CONFIG_SERIAL_8250_FSL is not set` to `disable-unused.cfg` and run `bitbake -c configure virtual/kernel`. The task succeeds, but the check loop prints `CONFIG_SERIAL_8250_FSL=y`. `$S/drivers/tty/serial/8250/Kconfig` explains why:

```
tristate "Freescale 16550 UART support" if COMPILE_TEST && !(PPC || ARM || ARM64)
default SERIAL_8250 if PPC || ARM || ARM64
```

On ARM64 the option has no prompt, so only the `default` counts. Remove the line again: a fragment line that does nothing is misleading, like `INPUT_POLLDEV` was.

---

### Step 7. Build, flash, check

```sh
kas-container build kas-project.yml
```

On the board: `uname -r` is `6.12.57-beagley-ti`, `lsmod` shows the same 53 modules as before, PCI and USB show the same devices.

Before the lab, about 100 of the 170 MiB rootfs were the kernel: `bb.org_defconfig` is a generic "distro" configuration and the image installs every module (`kernel-modules`).

| What | Before | After | Change |
|---|---|---|---|
| Kernel options `=y` / `=m` | 2348 / 2954 | 2019 / 2226 | −329 / −728 |
| `Image` | 40.0 MiB | 34.0 MiB | −15 % |
| `modules-beagley-custom.tgz` | 34.8 MB | 15.0 MB | −57 % |
| Installed modules (`.ko.xz`) | 2965 | 2224 | −741 |
| `/usr/lib/modules` in the rootfs | 45 MiB | 23 MiB | −49 % |
| `/boot` in the rootfs | 55 MiB | 47 MiB | −8 MiB |
| rootfs, total | 170 MiB | 139 MiB | −18 % |
| `rootfs.wic` / `rootfs.wic.xz` | 443 / 85 MB | 389 / 60 MB | −12 % / −30 % |
| Board, `df -h /` size / used | 274.5M / 146.7M | 228.7M / 118.2M | −28.5 MiB used |
| Board, `lsmod` | 53 | 53, the same | nothing we use was lost |

The ext4 partition shrinks too, because wic sizes it from the rootfs (`IMAGE_OVERHEAD_FACTOR`). `/boot` (the duplicate kernel) is now bigger than all the modules.

---

### Pitfalls on the way

* **`kas-container shell -c "bitbake -c menuconfig ..."` closes at once.** `log.do_menuconfig` shows BitBake trying the terminals and ending with `tmux new -d` (detached). `-c` returns, and the container stops together with the menu.
* **`diffconfig` wrote no `fragment.cfg`.** Nothing had been changed and saved, so `.config` and `.config.orig` were identical. In that case `do_diffconfig` writes nothing and even deletes an old fragment.
* **`make localmodconfig`** (disable every module not in `lsmod`) was considered and not used: it drops everything not loaded at that moment (USB storage, USB serial, UVC, the camera), does not touch built-in options, and produces a full `.config` instead of a fragment.

---

🎯 The kernel configuration of our layer is now really applied through `KERNEL_CONFIG_FRAGMENTS`. `uname -r` proves it, the kernel no longer carries drivers for hardware the BeagleY-AI does not have, and the image is 30 % smaller compressed with the same working hardware.
