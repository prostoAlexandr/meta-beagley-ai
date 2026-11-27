In this lab we set up the host (Ubuntu/Debian), download the Yocto Project metadata, build a minimal Linux image for the BeagleY-AI and boot it with a UART console.

---

# Lab No. 1: First Yocto Project build

### Objectives

1. Prepare the host system and download Poky and the BSP layers.
2. Configure `conf/local.conf` and `conf/bblayers.conf` for the BeagleY-AI.
3. Build `core-image-minimal` and flash it to an SD card.
4. Connect a USB-UART adapter and log in on the board.

---

### Step 1. Prepare the host system

Make sure your home directory is not encrypted with eCryptFS. OpenEmbedded does not support eCryptFS because of its file name length limits.

Install the required packages on Ubuntu/Debian:

```
sudo apt update
sudo apt install bmaptool build-essential chrpath cpio debianutils diffstat file gawk \
  gcc git iputils-ping libacl1 libcrypt-dev locales python3 python3-git \
  python3-jinja2 python3-pexpect python3-pip python3-subunit socat texinfo unzip \
  wget xz-utils zstd
```

---

### Step 2. Download Poky and the BSP layers

The BeagleY-AI is supported by the current Yocto LTS release, **Scarthgap** (Yocto 5.0).

Create a working directory and clone the repositories:

```
mkdir -p ~/Documents/Beagley_ai/yocto-labs && cd ~/Documents/Beagley_ai/yocto-labs
git clone -b scarthgap https://git.yoctoproject.org/git/poky
cd poky

# Supporting layers and the TI / BeagleBoard BSP
git clone -b scarthgap https://git.openembedded.org/meta-openembedded
git clone -b scarthgap https://git.yoctoproject.org/meta-arm
git clone -b scarthgap https://git.yoctoproject.org/meta-ti
```

---

### Step 3. Initialize the build environment

Run the initialization script. It creates the build directory and exports the required environment variables:

```
source oe-init-build-env
```

The script creates the `build/` directory and changes into it.

---

### Step 4. Configure `conf/local.conf`

Open `conf/local.conf` and change the target machine from the default `qemu` emulator to **beagley-ai**:

1. Set the machine:

   ```
   MACHINE = "beagley-ai"
   ```

2. Make sure the distribution is `poky`:

   ```
   DISTRO = "poky"
   ```

3. Allow `root` login without a password for easier debugging:

   ```
   EXTRA_IMAGE_FEATURES:append = " debug-tweaks"
   ```

4. **(Optional)** To save disk space, add:

   ```
   INHERIT += "rm_work"
   ```

---

### Step 5. Add the layers to `conf/bblayers.conf`

Edit `conf/bblayers.conf` and add the BSP layers to the `BBLAYERS` variable:

```
BBLAYERS ?= " \
  ${TOPDIR}/../meta \
  ${TOPDIR}/../meta-poky \
  ${TOPDIR}/../meta-yocto-bsp \
  ${TOPDIR}/../meta-openembedded/meta-oe \
  ${TOPDIR}/../meta-arm/meta-arm \
  ${TOPDIR}/../meta-arm/meta-arm-toolchain \
  ${TOPDIR}/../meta-ti/meta-ti-bsp \
  ${TOPDIR}/../meta-ti/meta-beagle \
  "
```

---

### Step 6. Start the build

Build the minimal image with BitBake:

```
bitbake core-image-minimal
```

*Note:* the first build can take from one to several hours, depending on the CPU and the Internet connection.

---

### Step 7. Flash the image to an SD card

meta-ti deploys all images to a common directory shared by the A53 and R5 builds: `deploy-ti/images/beagley-ai/` (not `tmp/deploy/images/`). For TI K3 it contains the bootloaders (`tiboot3.bin`, `tispl.bin`, `u-boot.img`) and the compressed SD card image `.wic.xz`.

Insert the SD card into the PC and flash it with `bmaptool`:

```
sudo bmaptool copy --removable-device deploy-ti/images/beagley-ai/core-image-minimal-beagley-ai.rootfs.wic.xz /dev/sdX
```

Replace `/dev/sdX` with your SD card device (check with `lsblk`).

---

### Step 8. Set up access to the serial port on the host

To open serial devices (`/dev/ttyUSB0` or `/dev/ttyACM0`) without `sudo`, add your user to the `dialout` group:

```
sudo adduser $USER dialout
```

(or `sudo usermod -aG dialout $USER`)

> **Important:** log out and log in again for the new group to take effect. To apply it in the current terminal only, run `newgrp dialout`.

Install the `picocom` terminal program:

```
sudo apt install picocom
```

---

### Step 9. Connect the USB-UART adapter to the BeagleY-AI

1. Use a USB-UART adapter with **3.3 V** signal levels.
2. Connect the adapter to the board's debug UART header:
   * adapter **GND** → board **GND**;
   * adapter **TX** → board **RX**;
   * adapter **RX** → board **TX**.
3. Plug the adapter into the PC and find the device name with `dmesg` or `ls /dev/ttyUSB* /dev/ttyACM*`.

Open the console at 115200 baud:

```
picocom -b 115200 /dev/ttyUSB0
```

Replace `/dev/ttyUSB0` with `/dev/ttyACM0` if your adapter shows up that way.

> To exit `picocom`, press **Ctrl+A**, then **Ctrl+X**.

---

### Step 10. Boot the board

1. Insert the flashed SD card into the board.
2. Power the BeagleY-AI through the USB Type-C connector.
3. In `picocom` you will see the output of the R5 SPL (`tiboot3.bin`), the A53 SPL (`tispl.bin`), U-Boot and the Linux kernel boot log.
4. Wait for the login prompt:

   ```
   beagley-ai login:
   ```

5. Log in as **root**. No password is needed because we enabled `debug-tweaks` in `local.conf`.

---

🎯 You have built your first Yocto image for the BeagleY-AI, flashed it to an SD card and logged in through the UART console.
