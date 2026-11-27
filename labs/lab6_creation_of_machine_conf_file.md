In this lab we take an important step in understanding a BSP (*Board Support Package*) in the Yocto Project: we write our **own machine configuration file**.

Until now we used the ready-made `MACHINE = "beagley-ai"`. Now we create our own machine **beagley-custom** in the `meta-beagley-labs` layer and see how BitBake ties together the kernel, the bootloader, the CPU architecture and the output image types.

---

# Lab No. 6: Creating a custom machine configuration (`beagley-custom.conf`)

### Objectives

1. Understand the purpose of BSP machine configuration files (`conf/machine/*.conf`).
2. Learn the key hardware description variables (`PREFERRED_PROVIDER`, `KERNEL_DEVICETREE`, `IMAGE_FSTYPES`, `MACHINE_FEATURES`).
3. Create `beagley-custom.conf` in the `meta-beagley-labs` layer.
4. Switch the project to `MACHINE = "beagley-custom"`.
5. Build `beagley-lab-image` for the new machine and fix the multiconfig error that appears.

---

### Theoretical introduction: what a `machine/*.conf` file defines

A machine configuration file describes the hardware of the target board for all recipes of the project:

* **CPU architecture and tuning:** which cross compiler to use (`aarch64`, `armv7a`, ...).
* **Kernel and bootloader providers:** which recipes implement `virtual/kernel` and `virtual/bootloader`.
* **Device Trees:** which `.dtb` files are built with the kernel (`KERNEL_DEVICETREE`).
* **Image formats:** whether the root filesystem is packed as `.tar.xz`, `.wic`, `.ext4` or `.ubifs` (`IMAGE_FSTYPES`).
* **Hardware features (`MACHINE_FEATURES`):** Wi-Fi, Bluetooth, RTC, display interfaces and so on.

---

### Step 1. Create the machine configuration directory

Go to the `meta-beagley-labs` layer and create `conf/machine`:

```
cd ~/Documents/Beagley_ai/yocto-labs/poky/meta-beagley-labs
mkdir -p conf/machine
```

The layer now looks like this:

```
meta-beagley-labs/
├── conf/
│   ├── layer.conf
│   └── machine/
│       └── beagley-custom.conf   <-- our new file
├── recipes-core/
├── recipes-games/
└── recipes-kernel/
```

---

### Step 2. Write `beagley-custom.conf`

Create the file:

```
nano conf/machine/beagley-custom.conf
```

In Yocto a machine can inherit the settings of an existing board with `require` or `include` and override only what is needed:

```
#@TYPE: Machine
#@NAME: BeagleY-AI Custom Machine
#@DESCRIPTION: Custom machine configuration for BeagleY-AI board based on TI AM67A SOC

# 1. Inherit the base BeagleY-AI hardware settings from the TI / BeagleBoard meta layer
require conf/machine/beagley-ai.conf

# 2. Set the supported output image formats
# tar.xz is used for unpacking to NFS, wic for writing to an SD card
IMAGE_FSTYPES += "tar.xz wic"

# 3. Explicitly pin the preferred kernel provider
PREFERRED_PROVIDER_virtual/kernel ?= "linux-bb.org"

# 4. Additional machine hardware features
MACHINE_FEATURES:append = " usbhost vfat serial"
```

The `#@TYPE`, `#@NAME` and `#@DESCRIPTION` lines are only comments that describe the machine for people and tools.

---

### Step 3. Switch `conf/local.conf` to the new machine

1. Go to the build directory:

   ```
   cd ~/Documents/Beagley_ai/yocto-labs/poky/build
   ```

2. Open `conf/local.conf`:

   ```
   nano conf/local.conf
   ```

3. Replace `MACHINE = "beagley-ai"` with:

   ```
   MACHINE = "beagley-custom"
   ```

---

### Step 4. Check that BitBake sees the new machine

1. Check `MACHINE`:

   ```
   bitbake-getvar MACHINE
   ```

   *Expected:* `MACHINE="beagley-custom"`

2. Check the kernel provider and the image types for the new machine:

   ```
   bitbake-getvar PREFERRED_PROVIDER_virtual/kernel
   bitbake-getvar IMAGE_FSTYPES
   ```

   `IMAGE_FSTYPES` contains the `tar.xz wic` formats we added.

---

### Step 5. Build the image for the new machine

Start the build of the custom image:

```
bitbake beagley-lab-image
```

The build stops right away with an error like this:

```
ERROR: ... MACHINE=beagley-custom-k3r5 is invalid ...
```

#### Why this error occurs

The TI AM67A SoC on the BeagleY-AI has two kinds of CPU cores:

1. **32-bit Cortex-R5F**: runs the very first boot stage (R5 SPL, `tiboot3.bin`).
2. **64-bit Cortex-A53**: runs the Linux kernel, the drivers and the rootfs.

To build both architectures in one run, meta-ti uses the BitBake **multiconfig** feature. With `MACHINE = "beagley-custom"`, BitBake runs two builds:

* for the 64-bit A53 it uses `conf/machine/beagley-custom.conf` (the file we created);
* for the 32-bit R5 it adds the `-k3r5` suffix and looks for **`conf/machine/beagley-custom-k3r5.conf`**.

Our layer had no `beagley-custom-k3r5.conf`, hence the error.

#### Fix: add the R5 companion machine

Create a companion file for the R5 core that inherits the R5 settings of the original BeagleY-AI.

1. Go to the machine configuration directory of our layer:

   ```
   cd ~/Documents/Beagley_ai/yocto-labs/poky/meta-beagley-labs/conf/machine
   ```

2. Create `beagley-custom-k3r5.conf`:

   ```
   nano beagley-custom-k3r5.conf
   ```

3. Write one line in it:

   ```
   require conf/machine/beagley-ai-k3r5.conf
   ```

---

### Step 6. Build again

```
cd ~/Documents/Beagley_ai/yocto-labs/poky/build
bitbake beagley-lab-image
```

BitBake now finds both files (`beagley-custom.conf` for the kernel and the image, `beagley-custom-k3r5.conf` for the R5 bootloader) and the build succeeds. The machine inherits `beagley-ai.conf`, so most packages come from the `sstate-cache` and are not rebuilt.

The artifacts are deployed to `deploy-ti/images/beagley-custom/` (meta-ti uses a common deploy directory for the A53 and R5 builds). The R5 part is built in `tmp-k3r5/`.

---

🎯 Your project now builds for its own machine `beagley-custom`, and you know why every K3 machine in meta-ti needs a `-k3r5` companion.
