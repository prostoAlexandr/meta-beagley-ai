# Planned labs

Labs to do in the future. Each entry has a goal, the background (why it is needed), a rough plan and a "done when" check. When a lab is written, move it out of this list into its own `labN_<name>.md` file.

Known issues of the repository are tracked here as well, as labs that fix them.

The list has two parts:

* **Fundamentals:** labs that cover the gaps of the learning path (see "Learning path" at the end).
* **Features and fixes:** labs that add features to the firmware or fix known issues.

---

# Fundamentals

## Manual boot and TFTP from the U-Boot console

**Goal:** boot Linux by hand from U-Boot, without extlinux, and then load the kernel and DTB over the network.

**Background:** Lab 10 showed the boot chain and the U-Boot standard boot. This lab makes each step explicit. It is also the "Going further" part of the original Bootlin Lab 2: no SD card reflash for every kernel test.

**Plan:**
- Draw the boot chain ROM → `tiboot3.bin` → `tispl.bin` → `u-boot.img` → Linux: which file, from where, loaded by whom.
- Boot from the U-Boot console with `load mmc 1:1 ${kernel_addr_r} /Image`, `load ... ${fdt_addr_r} ...`, `setenv bootargs ...` and `booti`.
- Apply an overlay by hand with `fdt addr`, `fdt resize` and `fdt apply`, and compare with `FDTOVERLAYS`.
- Install `tftpd-hpa` on the host, put `Image` and the DTB there, and load them with `tftp`.
- Find out why `saveenv` does not work here (`Loading Environment from nowhere`) and use `uEnv.txt` or a boot script instead.

**Done when:** the board boots a kernel from TFTP with the rootfs over NFS, without touching the SD card.

---

## Writing and packaging your own DT overlay

**Goal:** write a Device Tree overlay from scratch, build it with Yocto and enable it through `extlinux.conf`.

**Background:** Lab 10 enabled only ready-made overlays from the kernel. To understand `__symbols__`, `__fixups__` and phandles, you have to write one yourself.

**Plan:**
- Choose simple hardware: an LED or a button on a GPIO of the 40-pin header, or an I2C device.
- Write the `.dtso` overlay with a label reference (`&main_gpio1`, `&main_i2c1`, ...).
- Write a recipe that compiles it with `dtc -@` (or `inherit devicetree`) and deploys the `.dtbo`. Add it to `IMAGE_BOOT_FILES`.
- Enable it with `FDTOVERLAYS` and check the node in `/proc/device-tree` and the driver in `dmesg`.
- Break it on purpose (a wrong label) and see `FDT_ERR_NOTFOUND`, then boot `safe`.

**Done when:** the LED (or device) works after a reboot with our own overlay, and the overlay is built and deployed by our layer.

---

## Variables, overrides and layer priorities

**Goal:** understand how BitBake combines variable assignments from many files and layers: "the logic of overlaying".

**Background:** most surprises so far came from this: `?=` in `k3.inc` vs `:append` in our machine, `:remove = "efi"`, the order of `require poky.conf` in the distro (Lab 9), the `:beagley-ai` override in Lab 2, the `FILESOVERRIDES` lookup of `fstab` (Lab 10).

**Plan:**
- Assignment operators: `=`, `?=`, `??=`, `:=`, `+=`, `.=`, `:append`, `:prepend`, `:remove`. Write a small test recipe and print the results with `bitbake-getvar`.
- Parse order: `bitbake.conf` → `local.conf` → machine → distro → recipe → `.bbappend`. Why a machine `=` beats `local.conf`.
- `OVERRIDES` and conditional syntax (`VAR:beagley-custom`, `VAR:aarch64`, `VAR:pn-<recipe>`).
- Read the assignment history of a real variable with `bitbake -e` (for example `WKS_FILE`, `IMAGE_BOOT_FILES`, `MACHINE_FEATURES`).
- Layer priority: create the same recipe in two layers and check which one wins with `bitbake-layers show-overlayed`. Also `PREFERRED_VERSION` and the order of `.bbappend` files.
- File lookup: `FILESPATH`, `FILESEXTRAPATHS` and `FILESOVERRIDES`.

**Done when:** for any variable of the image you can tell which file set its final value and why, from the `bitbake -e` history alone.

---

## Build analysis: task graph, buildhistory and sstate

**Goal:** see how a recipe flows through the build pipeline and what changes in the image after an edit.

**Background:** the difference between `do_install` (packages → rootfs) and `do_deploy` (`DEPLOY_DIR_IMAGE` → wic) was the key point of Lab 10. This lab makes the whole pipeline visible.

**Plan:**
- `bitbake -c listtasks` and `bitbake -g beagley-ai-image`: find in `task-depends.dot` how `extlinux-conf:do_deploy` reaches `do_image_wic`.
- Follow one recipe through `do_fetch` → `do_install` → `do_package` → `do_package_write_rpm` → `do_rootfs`, and find its files in `image/`, `packages-split/` and the rootfs.
- `oe-pkgdata-util find-path /boot/Image` and `oe-pkgdata-util list-pkg-files <pkg>`.
- Enable `INHERIT += "buildhistory"` and compare the image before and after a change (`buildhistory-diff`).
- sstate: why a rebuild takes minutes, `bitbake-dumpsig` / `bitbake-diffsigs` to see why a task was re-run.

**Done when:** you can explain for any file on the SD card which recipe and task produced it, and for any rebuild why it happened.

---

## Reading the vendor BSP (meta-ti)

**Goal:** understand what meta-ti sets up for the BeagleY-AI, instead of treating it as magic.

**Background:** the `k3r5` multiconfig (Lab 6), `TI_COMMON_DEPLOY` / `deploy-ti` (Lab 1), the `efi` machine feature and the WKS selection (Lab 10) all come from meta-ti include files.

**Plan:**
- Read the chain `beagley-ai.conf` → `j722s.inc` → `k3.inc` → `ti-soc.inc` → `ti-bsp.inc` → `beagle-bsp.inc` and write down every variable that affects boot, kernel and deploy.
- `TI_PREFERRED_BSP` and the multi-BSP mechanism (`bsp-bb_org-6_12` overrides): what changes if you switch to the TI kernel.
- `conf/multiconfig/k3r5.conf` and how `tiboot3.bin` is built in `tmp-k3r5`.
- Compare with the TI Processor SDK documentation for AM67 and the boot ROM chapter of the J722S TRM.

**Done when:** you can draw which meta-ti file sets `PREFERRED_PROVIDER_virtual/kernel`, `UBOOT_MACHINE`, `WKS_FILE` and `DEPLOY_DIR_IMAGE` for `beagley-custom`.

---

# Features and fixes

## System updates and the duplicated kernel / DTBs

**Goal:** update the system on the board with a package manager, so that the updated kernel and DTBs are the ones that boot.

**Background (known issue):** the kernel and DTBs exist in two copies. U-Boot boots `Image` and `dtb/ti/*.dtb` from the FAT partition, which wic fills from `DEPLOY_DIR_IMAGE` (`IMAGE_BOOT_FILES`) at image build time. The packages `kernel-image-image` (`MACHINE_ESSENTIAL_EXTRA_RDEPENDS` in meta-ti `k3.inc`) and `kernel-devicetree` (`ti-soc.inc`) also install them into the rootfs `/boot`, which nothing reads at boot. Updating the kernel package on the target would change only `/boot`, and the old kernel would still boot. Since Lab 11 trimmed the modules, this `/boot` copy (47 MiB) is the largest part of the rootfs (139 MiB).

**Plan:**
- Add `package-management` to `IMAGE_FEATURES`, pick the package format (`PACKAGE_CLASSES`) and serve a package feed from the host.
- Update a simple package (for example `ninvaders`) on the board from the feed.
- Make the kernel and DTB packages land where U-Boot reads them: for example install them into `/boot/firmware`, or post-install them onto the FAT partition. Remove the duplicate copy.
- Optional follow-up: look at full-image A/B updates (SWUpdate, RAUC, Mender).

**Done when:** after a kernel package update on the board, `uname -a` shows the new kernel after a reboot, and there is only one copy of the kernel and DTBs.

---

## Updating the kernel version

**Goal:** move from the 6.12 kernel to a newer one, and learn how Yocto selects the kernel version and what a kernel update can break, the Device Tree first of all.

**Background:** the kernel is chosen in steps: `TI_PREFERRED_BSP` (`bb_org-6_12` in meta-beagle `beagle-bsp.inc`) sets `BSP_KERNEL_PROVIDER` / `BSP_KERNEL_VERSION` (and the U-Boot and GPU driver versions), which become `PREFERRED_PROVIDER_virtual/kernel` and `PREFERRED_VERSION`; our machine sets `PREFERRED_PROVIDER_virtual/kernel ?= "linux-bb.org"`. What we found (checked 2026-11-28):

* **Our kernel is TI's kernel plus BeagleBoard.org patches.** `linux-bb.org` 6.12.57, branch `v6.12.57-ti-arm64-r64` of `github.com/beagleboard/linux`, is TI's `ti-linux-6.12.y` (TI SDK 11.02.13, with the stable `v6.12.57` merged) plus about 30 BeagleBoard.org commits: the board DTS and 32 BeagleY-AI overlays (BeagleBoard-DeviceTrees), `bb.org_defconfig`, wireless-regdb, `tps65219` (PMIC) backports, crypto fixes from 6.12.84, and drivers we do not use (Coral `gasket`, imx708 camera, `mspm0`, overlay configfs, binder/ashmem). The newest branch there is `r65`; there is no 6.18 branch.
* **meta-ti `scarthgap`** (our branch): only 6.6 and 6.12, for `linux-bb.org` and TI's `linux-ti-staging`.
* **meta-ti `wrynose` / `master`** (newer Yocto releases, `LAYERSERIES_COMPAT` `whinlatter wrynose`): the same board has two machines. `beagley-ai-ti.conf` describes the board (`j722s.inc`, U-Boot `am67a_beagley_ai_a53_defconfig`, all `ti/k3-am67a*` DTBs) and uses TI's BSP: `linux-ti-staging` **6.18.63** and `u-boot-ti-staging` 2026.01. `beagley-ai.conf` = `beagley-ai-ti.conf` + `beagle-bsp.inc`, and stays on `linux-bb.org` **6.12**. So a Yocto update alone does not bring 6.18.
* **The Device Tree comes with the kernel source, so it changes with the kernel.** Mainline v6.18 `k3-am67a-beagley-ai.dts` has 402 lines and no BeagleY-AI overlays; it enables only Ethernet, the console UART, the PMIC I2C, the SD card and GPIO1. Our bb.org DTS (946 lines) also enables USB, PCIe, Wi-Fi/Bluetooth (CC33xx on `sdhci2`), HDMI (`dss1`), audio (`mcasp1`), the fan (`cooling-maps`), I2C1/GPIO0 of the 40-pin header and more UARTs. What TI's own 6.18 tree adds for the BeagleY-AI is **not checked yet** (git.ti.com was not reachable).
* **A newer Yocto release breaks our layer:** files from `SRC_URI` are unpacked into `UNPACKDIR`, not `WORKDIR` (meta-ti `wrynose` already uses `S = "${UNPACKDIR}/..."`). We hard-code `${WORKDIR}` in `linux-bb.org_%.bbappend` (both fragments) and in `extlinux-conf_1.0.bb`.

**How the Device Tree can break on a new kernel:** the DTB format (FDT) is stable, so any kernel parses any DTB. What can differ is the meaning, the bindings: a driver finds its node by `compatible` and reads the properties described in `Documentation/devicetree/bindings/`. Mainline keeps old DTBs working ("DT is ABI", `Documentation/devicetree/bindings/ABI.rst`), but vendor bindings (`PENDING:`, `FROMLIST:` patches of TI, BeagleBoard.org additions) may change. Symptoms: the device silently does not appear (no driver for the `compatible`), a probe error in `dmesg` (missing or changed property), a device stuck in deferred probe, and in the worst case (power, clocks, pinmux) no SD card or console. Overlays reference labels of the base DTB (`__symbols__`) and fail with `FDT_ERR_NOTFOUND` if a label was renamed, so they must be built from the same base DTS.

The kernel is also tied to our two config fragments (a renamed or removed option is dropped silently, Lab 11), the DTB name and overlays in `extlinux.conf` (Lab 10), the out-of-tree GPU driver (`ti-img-rogue-driver`, version set per BSP) and U-Boot (set per BSP too).

**Plan:**
- Warm-up, a minor update within 6.12: override `BRANCH` / `SRCREV` in our bbappend to `r65`, read the difference (`git log` between the two tags), rebuild, check. Learn `SRCREV`, `PV`, `AUTOREV` and why a pinned commit keeps the build reproducible.
- Save the reference on 6.12 for later comparison: `lsmod` (53 modules), the bound drivers (loop from Lab 11), `dmesg`, `ls /sys/bus/*/devices`, and the decompiled DTB on the host (`dtc -I dtb -O dts` of `k3-am67a-beagley-ai.dtb` from `DEPLOY_DIR_IMAGE`; `dtc` is not in the image).
- Check the TI 6.18 Device Tree before choosing a path: build TI's `k3-am67a-beagley-ai.dtb` (or read it in the TI tree) and compare it with ours: which of USB, PCIe, Wi-Fi/BT, HDMI, audio, fan, 40-pin interfaces are enabled, which overlays exist.
- Compare the ways to 6.18:
  - **A.** Stay on scarthgap, backport `linux-ti-staging_6.18.bb` (and what it needs: U-Boot, GPU driver versions) from meta-ti `wrynose` into our layer, switch `PREFERRED_PROVIDER_virtual/kernel` / `TI_PREFERRED_BSP`.
  - **B.** Mainline (`linux-ti-mainline_git.bb` with a newer `SRCREV`): too little of the board is in the upstream DTS, mostly for comparison.
  - **C.** First move the project to `wrynose` while staying on `linux-bb.org` 6.12 (kas refspecs, `LAYERSERIES_COMPAT`, `WORKDIR` → `UNPACKDIR`), then switch the machine to `beagley-ai-ti` for 6.18. Probably two labs.
  - **D.** TI 6.18 kernel with the BeagleY-AI DTS and overlays from BeagleBoard.org ported into it (patch or own DTS in the layer, see "Patching the Device Tree in the kernel").
- Pick one and build it. Keep a way back: a second `extlinux.conf` label that boots the old 6.12 `Image` and DTB, next to `safe`.
- Check the Device Tree in three stages:
  - **Build:** `dtc` errors for renamed labels or header macros in the ported DTS.
  - **Schema:** `make dtbs_check` (dt-schema) validates our DTB against the YAML bindings of the new kernel and lists unknown or changed properties.
  - **Board:** `dmesg | grep -i -E 'probe|fail|error'`, `cat /sys/kernel/debug/devices_deferred`, the bound drivers and `lsmod` against the 6.12 reference.
- Check the rest: the Lab 11 check loop for both fragments (the defconfig changes too: TI uses `defconfig` + `ti_arm64_prune.config` instead of `bb.org_defconfig`), `KERNEL_DEVICETREE` and the DTB path in `extlinux.conf`, the overlays (rebuilt from the new base DTS), the GPU driver build.
- On the board: `uname -r`, Ethernet, Wi-Fi/Bluetooth, USB, HDMI/audio, the fan.

**Done when:** the board boots the newer kernel from our image with the old one still selectable in the menu, the fragments and the Device Tree are verified (`dtbs_check`, no unexpected deferred devices), and every difference in hardware support against the 6.12 reference is either fixed or documented.

---

## Replacing Dropbear with OpenSSH

**Goal:** use the full OpenSSH server instead of Dropbear.

**Background:** Dropbear is small but has no SFTP server. This is why `openssh-sftp-server` was added to the image next to it (modern `scp` uses SFTP, see Lab 8).

**Plan:**
- Learn how SSH servers are selected in images: the `ssh-server-dropbear` and `ssh-server-openssh` `IMAGE_FEATURES`, and why they conflict.
- Replace `dropbear` in `beagley-ai-image.bb` with OpenSSH (`openssh`, `openssh-sshd`, `openssh-sftp-server`).
- Configure `sshd` for the lab setup (root login, `debug-tweaks`) and see where the host keys are generated and stored.
- Check `ssh`, `scp` and `sftp` from the host, and `devtool deploy-target`.

**Done when:** the rootfs manifest contains OpenSSH and no Dropbear, and `ssh`, `scp` (without `-O`) and `sftp` work.

---

## TrustZone and OP-TEE

**Goal:** understand how TrustZone works on the AM67A, as an example: secure and normal world, and a trusted application called from Linux.

**Background:** OP-TEE is already part of the boot chain. It is packed into `tispl.bin` together with TF-A (see `bl31.bin` and `bl32.bin` in `deploy-ti/images/beagley-custom/`), but nothing in Linux uses it yet.

**Plan:**
- Trace where TF-A (BL31) and OP-TEE (BL32) are built and packed (`meta-arm`, meta-ti `k3.inc`), and find the OP-TEE messages in the boot log.
- Add `optee-client` (`tee-supplicant`, `libteec`) and `optee-test` (`xtest`) to the image, and check `/dev/tee*`.
- Build and run a "hello world" trusted application (for example from `optee-examples`).
- Read about what runs in the secure world on K3 and what HS-FS (the security level of this SoC) means for it.

**Done when:** `xtest` passes on the board and a trusted application of our own returns a result to a Linux program.

---

## Patching the Device Tree in the kernel

**Goal:** ship our own Device Tree changes as the default hardware description of our firmware, instead of an overlay that is enabled by hand.

**Background:** overlays (Lab 10) are good for optional hardware that you turn on at boot. Changes that every board running our firmware must have belong in the base DTB.

**Plan:**
- Choose a change (for example enable an interface by default, or change a pin configuration).
- Option A: patch `k3-am67a-beagley-ai.dts` in the `linux-bb.org` sources with `devtool modify` and `devtool finish`, so the patch ends up in our bbappend (`git format-patch` format with an `Upstream-Status:` header).
- Option B: add our own `k3-am67a-beagley-custom.dts` that includes the BeagleY-AI DTS, and add it to `KERNEL_DEVICETREE` and to `extlinux.conf`.
- Compare both options with an overlay: maintenance, kernel updates, upstreaming.
- Check the result in `/proc/device-tree` on the board.

**Done when:** the change is active right after flashing the image, with no `FDTOVERLAYS` line in `extlinux.conf`.

---

## Boot-time services audit

**Goal:** list everything that starts when the board boots, understand what each item does and why it is there, and keep only what we need.

**Background:** the image is based on `core-image-minimal` with systemd, but `packagegroup-core-boot`, `kernel-modules`, `MACHINE_EXTRA_RRECOMMENDS` from meta-ti and systemd presets still start services and load modules that we never chose on purpose.

**Plan:**
- Make the list on the board: `systemctl list-units --type=service`, `systemctl list-unit-files --state=enabled`, `lsmod`, and the boot log (`journalctl -b`, `dmesg`). Note which messages and errors appear at every boot.
- Measure the boot time: `systemd-analyze`, `systemd-analyze blame`, `systemd-analyze critical-chain`.
- For each service and module, find the package that installed it (`oe-pkgdata-util find-path`, the rootfs manifest) and the variable that pulled the package in (`bitbake -g`, `bitbake -e`).
- Decide what stays and why. Write the result as a table: name, purpose, who pulls it in, keep or remove.
- Remove what is not needed, the Yocto way: `IMAGE_FEATURES`, `DISTRO_FEATURES`, `BAD_RECOMMENDATIONS` / `NO_RECOMMENDATIONS`, `PACKAGE_EXCLUDE`, `SYSTEMD_AUTO_ENABLE` or a systemd preset in a `.bbappend`. Replace `kernel-modules` (all modules) with an explicit list if that makes sense: after Lab 11 the image still installs 2224 modules, and 53 are loaded at boot.
- Check the hardware that Lab 11 kept but did not test: a USB stick (`USB_STORAGE`), a USB-UART adapter (`USB_SERIAL_*`). Optionally drop more from `disable-unused.cfg` (bonding/team, PPP/SLIP, WireGuard were kept).

**Done when:** every service and module that starts at boot is in the table with a reason, the unneeded ones are gone from the image, and the boot time before and after is recorded.

---

## TI firmware and accelerators: C7x DSP / MMA (NPU), GPU, Wi-Fi

**Goal:** find out why the hardware blocks that work in the TI Edge AI SDK image do not work in our firmware, and enable them in our image.

**Background:** the kernel side mostly exists already. The image has `kernel-module-ti-k3-dsp-remoteproc`, `kernel-module-ti-k3-r5-remoteproc` and `kernel-module-cc33xx-sdio`, and the kernel has `CONFIG_DRM_POWERVR=m`. What is missing is the **firmware and the user-space parts**, which are partly proprietary (TI licenses) and come from other layers:

* **C7x DSP with the MMA deep learning accelerator ("NPU"):** firmware for the remote cores (`/lib/firmware/j722s-c71_0-fw`, already listed in the U-Boot `rproc_fw_binaries` variable) from `ti-rtos-fw` in `meta-ti-bsp`, and the AI stack (TIDL, TIOVX, the vision apps firmware) from the TI Edge AI SDK layers.
* **GPU (IMG Rogue):** `ti-img-rogue-driver` and `ti-img-rogue-umlibs` in `meta-ti-bsp` (`PREFERRED_PROVIDER_virtual/gpudriver` in `j722s.inc`).
* **Wi-Fi (CC33xx, the BeagleY-AI BM3301 module):** `cc33xx-fw`, `cc33conf` and `cc33xx-target-scripts` are in **`meta-ti-extras`**, which is not in our `kas-project.yml` yet.

**Plan:**
- Compare with the official image: boot `beagley-ai-edge-ai-11.00.00.08` (already on disk) and record `lsmod`, `/lib/firmware`, `ls /sys/class/remoteproc/*/state`, `dmesg` for remoteproc, GPU and Wi-Fi. Do the same on our image.
- From the difference, list the missing packages and find their recipes and layers (`meta-ti-bsp`, `meta-ti-extras`, TI Edge AI / Processor SDK layers such as `meta-edgeai`).
- Check the licenses (`LICENSE`, `LICENSE_FLAGS`) and what has to be accepted in our distro to build them.
- Enable one block at a time:
  - **Remote cores:** install the C7x / R5 firmware and start a core through `/sys/class/remoteproc` (echo test first, then the AI firmware).
  - **GPU:** add the Rogue driver and user-space libraries, check `/dev/dri` and a simple OpenGL ES test.
  - **Wi-Fi:** add `meta-ti-extras` to `kas-project.yml` (and `kas-project.lock.yml`), install the CC33xx firmware and tools, connect to a network.
  - **NPU:** run a TIDL inference example and compare it with a CPU-only run.

**Done when:** for each of DSP / NPU, GPU and Wi-Fi it is either working on our image with a test that proves it, or documented why it is not (license, missing layer, SDK version mismatch).

---

# Learning path

The plan goes from the foundation (what boots) to the build system on top of it (how Yocto builds it).

| Stage | Topic | Done | Planned |
|---|---|---|---|
| 1 | Boot chain without Yocto: ROM, U-Boot, kernel, rootfs | Lab 1, Lab 10 | Manual boot and TFTP |
| 2 | Device Tree and overlays | Lab 10 | Writing your own DT overlay; Patching the Device Tree |
| 3 | Yocto core concepts: task pipeline, packages vs deploy, sstate | Labs 2–4, Lab 10 | Build analysis |
| 4 | Variables, overrides, layers | Labs 2, 4–6, 9 | Variables, overrides and layer priorities |
| 5 | Vendor BSP (meta-ti) | Lab 6 (`k3r5`) | Reading the vendor BSP |
| 6 | System updates | | System updates; then A/B updates |
| — | Developer tools | Lab 7 (devtool), Lab 8 (SDK) | |
| — | Kernel and security | Lab 5, Lab 11 | Updating the kernel version; TrustZone and OP-TEE; OpenSSH |
| — | Image content and hardware enablement | | Boot-time services audit; TI firmware and accelerators |

### How to investigate on your own

| Question | Tool |
|---|---|
| Where does the value of a variable come from? | `bitbake -e <recipe> \| less`, search `^# \$VAR` for the assignment history |
| Which package installs a file? | `oe-pkgdata-util find-path <path>`, `oe-pkgdata-util list-pkg-files <pkg>` |
| Which tasks does a recipe have, in which order? | `bitbake -c listtasks <recipe>`, `bitbake -g <image>` |
| What changed in the image? | `INHERIT += "buildhistory"`, `buildhistory-diff` |
| Who overrides a recipe? | `bitbake-layers show-appends`, `bitbake-layers show-overlayed` |
| How do others do it? | `grep -rn VAR poky/meta/classes-recipe meta-ti/` |
| What does U-Boot do? | `bootflow scan -lae`, `printenv`, `help <cmd>`, then the sources in `build/tmp/work/.../u-boot-*/git/` |
| What did the kernel get? | `/proc/device-tree`, `dtc -I dtb -O dts <file>.dtb` |

### Ongoing practice

* Keep the **variable glossary** in `labs/yocto_glossary.md`: one line per new variable (what it does, who sets it, where you saw it). Group the variables by machine (`MACHINE_FEATURES`, `KERNEL_DEVICETREE`, `IMAGE_BOOT_FILES`, `WKS_FILE`), distro (`DISTRO_FEATURES`, `INIT_MANAGER`), image (`IMAGE_INSTALL`, `IMAGE_FEATURES`, `IMAGE_FSTYPES`), recipe (`SRC_URI`, `S`, `WORKDIR`, `FILES`, `RDEPENDS`, `PACKAGE_ARCH`) and file lookup (`FILESEXTRAPATHS`, `FILESOVERRIDES`).
* For every lab, write down what you wanted, what broke and where the answer was found.
* Break things on purpose, then read the docs. When asking for help, ask "where is this documented and how did you find out", not only "how".

### Resources

* **Bootlin** (free slides and labs, BeaglePlay variants with the AM62x, a close relative of the AM67A):
  * *Embedded Linux system development*: toolchain, U-Boot, kernel and rootfs by hand (stages 1–2). <https://bootlin.com/training/embedded-linux/>
  * *Yocto Project and OpenEmbedded development*: the base of Labs 1–9 (stages 3–4). <https://bootlin.com/training/yocto/>
  * Thomas Petazzoni, *Device Tree for Dummies* (ELC talk, stage 2).
* **Root Commit** free OpenEmbedded/Yocto training materials on BeaglePlay.
* **Yocto Project documentation** (scarthgap 5.0): *Overview and Concepts Manual* (build pipeline, sstate), *Reference Manual*, especially the *Variables Glossary*, *Tasks* and *Classes* chapters, and the *BitBake User Manual* (operators and overrides). <https://docs.yoctoproject.org/5.0/>
* **U-Boot documentation:** *Standard Boot* (bootstd), *Generic Distro Configuration* (extlinux), *Device Tree overlays*, *TI K3 boards*. <https://docs.u-boot.org/>
* **Linux kernel documentation:** `Documentation/devicetree/usage-model.rst`, `overlay-notes.rst`. The Devicetree specification: <https://www.devicetree.org/specifications/>
* **TI:** Processor SDK Linux for AM67 (*Foundational Components*), the J722S TRM (SPRUJB3, boot ROM chapter).
* **BeagleBoard.org:** BeagleY-AI documentation at <https://docs.beagleboard.org/>.
* **Books:** *Mastering Embedded Linux Programming* (F. Vasquez, C. Simmonds), *Embedded Linux Development Using Yocto Project* (O. Salvador, D. Angolini).
