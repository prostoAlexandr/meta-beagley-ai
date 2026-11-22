# meta-beagley-ai

A custom Yocto BSP/distro layer for the **BeagleY-AI** board (TI AM67A / J722S SoC,
4× Cortex-A53 + Cortex-R5F). The build is driven by [kas](https://kas.readthedocs.io/);
the layer repository is also the kas project.

The main purpose is educational (lab work): a minimal systemd-based image with SSH, kernel
modules, a custom kernel config fragment and a custom recipe.

## Contents

| Path | Purpose |
|---|---|
| `kas-project.yml` | kas configuration: machine, distro, target and the list of dependency layers |
| `kas-project.lock.yml` | pinned commits of the dependencies (reproducible builds) |
| `conf/layer.conf` | registers the `meta-beagley-ai` layer, priority 6, compatible with `scarthgap` |
| `conf/machine/beagley-custom.conf` | `beagley-custom` machine on top of `beagley-ai` from `meta-beagle` |
| `conf/machine/beagley-custom-k3r5.conf` | companion machine for the R5F core (required by the `k3r5` multiconfig) |
| `conf/distro/beagley-distro.conf` | `beagley-distro` distribution based on Poky with systemd |
| `recipes-core/images/beagley-ai-image.bb` | `beagley-ai-image` image based on `core-image-minimal` |
| `recipes-games/ninvaders/` | nInvaders 0.1.1 recipe |
| `recipes-kernel/linux/` | `bbappend` for `linux-bb.org` with a kernel config fragment |

### kas project

| Setting | Value |
|---|---|
| `machine` | `beagley-custom` |
| `distro` | `beagley-distro` |
| `target` | `beagley-ai-image` |

### Machine `beagley-custom`

- `require conf/machine/beagley-ai.conf` — inherits all BeagleY-AI hardware settings from
  `meta-ti/meta-beagle` (U-Boot, TF-A, OP-TEE, device trees, the default
  `sdimage-2part-efi.wks.in` SD card layout with GRUB EFI).
- `IMAGE_FSTYPES += "tar.xz wic"` — `tar.xz` for NFS root, `wic` for writing to an SD card
  (`wic.xz` and `wic.bmap` are already added by meta-ti's `k3.inc`).
- `PREFERRED_PROVIDER_virtual/kernel ?= "linux-bb.org"` — the BeagleBoard.org kernel (6.12).
- `MACHINE_FEATURES:append = " usbhost vfat serial"`.

**Why `beagley-custom-k3r5.conf` exists:** K3 platforms in meta-ti are built with the `k3r5`
multiconfig (R5 SPL / `tiboot3.bin`). The multiconfig derives the machine name as
`${MACHINE}-k3r5`, so `beagley-custom` requires a matching `beagley-custom-k3r5` machine.
It simply includes `beagley-ai-k3r5.conf`. The R5 part is built in `build/tmp-k3r5`.

### Distro `beagley-distro`

Based on `poky.conf`:

- `DISTRO_FEATURES:append = " systemd usrmerge pam"`;
- `VIRTUAL-RUNTIME_init_manager = "systemd"`.

### Image `beagley-ai-image`

`core-image-minimal` plus these packages:

- `dropbear` — SSH server;
- `openssh-sftp-server` — SFTP (for `scp`/`sftp` over dropbear);
- `kernel-modules` — all kernel modules;
- `ninvaders` — the console game from this layer.

### Kernel (`linux-bb.org_%.bbappend`)

Adds the `files/enable-extra-options.cfg` fragment:

```
CONFIG_INPUT_POLLDEV=y
CONFIG_I2C_CHARDEV=y      # /dev/i2c-* for user space
CONFIG_DYNAMIC_DEBUG=y
```

### nInvaders

- sources from SourceForge, licensed GPL-2.0-only;
- `CFLAGS += -std=gnu17 -fcommon` — the old code does not build with modern GCC without these flags;
- `EXTRA_OEMAKE` passes the cross compiler to the original Makefile;
- installed as `/usr/bin/ninvaders`.

## Dependencies

All branches are **scarthgap** (Yocto 5.0 LTS). Exact commits are in `kas-project.lock.yml`.

| Repository | URL | Layers |
|---|---|---|
| poky | https://git.yoctoproject.org/poky | `meta`, `meta-poky`, `meta-yocto-bsp` |
| meta-openembedded | https://git.openembedded.org/meta-openembedded | `meta-oe` |
| meta-arm | https://git.yoctoproject.org/meta-arm | `meta-arm`, `meta-arm-toolchain` |
| meta-ti | https://git.yoctoproject.org/meta-ti | `meta-ti-bsp`, `meta-beagle` |

kas clones them directly into the repository root (`poky/`, `meta-ti/`, …); these directories
and `build/` are listed in `.gitignore`.

## Building

Requires kas ≥ 4 (configuration format `header.version: 14`). The easiest way is
`kas-container`, which needs no Yocto host dependencies.

```sh
# build the image (default target is beagley-ai-image)
kas-container build kas-project.yml
# or natively
kas build kas-project.yml

# interactive shell with the BitBake environment
kas-container shell kas-project.yml

# build a single recipe
kas-container shell kas-project.yml -c "bitbake ninvaders"

# refresh the lock file after changing a refspec
kas lock --update kas-project.yml
```

When `kas-project.lock.yml` is present next to the project file, kas uses the pinned commits automatically.

### Build output

meta-ti redirects image deployment to a common directory shared by the A53 and R5 builds,
so all artifacts end up in **`build/deploy-ti/images/beagley-custom/`**:

```
beagley-ai-image-beagley-custom.rootfs.wic.xz    # SD card image
beagley-ai-image-beagley-custom.rootfs.wic.bmap  # block map for bmaptool
beagley-ai-image-beagley-custom.rootfs.wic       # uncompressed SD card image
beagley-ai-image-beagley-custom.rootfs.tar.xz    # rootfs for NFS
beagley-ai-image-beagley-custom.rootfs.manifest  # list of installed packages
tiboot3.bin, tispl.bin, u-boot.img               # bootloaders (R5 SPL, A53 SPL + TF-A/OP-TEE, U-Boot)
Image, fitImage, *.dtb, *.dtbo                   # kernel and device trees
modules-beagley-custom.tgz                       # kernel modules
```

Intermediate build trees: `build/tmp` (A53) and `build/tmp-k3r5` (R5).

### Flashing an SD card

Double-check the target device with `lsblk` first!

```sh
cd build/deploy-ti/images/beagley-custom
sudo bmaptool copy beagley-ai-image-beagley-custom.rootfs.wic.xz /dev/sdX
# or without bmaptool
xzcat beagley-ai-image-beagley-custom.rootfs.wic.xz | sudo dd of=/dev/sdX bs=4M status=progress conv=fsync
```

## Using the layer without kas

```sh
bitbake-layers add-layer meta-beagley-ai
```

In `local.conf`: `MACHINE = "beagley-custom"` and `DISTRO = "beagley-distro"`.
The layers from the dependency table must be added as well.

## License

MIT — see `COPYING.MIT`. The nInvaders recipe builds third-party software licensed under GPL-2.0-only.
