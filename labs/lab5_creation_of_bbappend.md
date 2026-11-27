In this lab we extend existing BSP and kernel recipes without editing their files, using **BitBake append files** (`.bbappend`). We also learn how to add patches to the Linux kernel and change its configuration with config fragments (`.cfg`).

---

# Lab No. 5: Writing a `.bbappend`, patching and configuring the Linux kernel

### Objectives

1. Understand how `.bbappend` files work and how `FILESEXTRAPATHS:prepend` changes the file search path.
2. Check which appends are applied with `bitbake-layers show-appends`.
3. Create a `.bbappend` for the BeagleY-AI kernel recipe in our layer `meta-beagley-labs`.
4. Add a configuration fragment (`.cfg`) to enable kernel options.
5. Learn how a patch file is added to `SRC_URI`.
6. Build the kernel with `bitbake virtual/kernel` and check the result.

---

### Theoretical introduction: anatomy of a `.bbappend`

#### 1. Naming and versions

A `.bbappend` file must have **exactly the same name** as the `.bb` recipe it extends.

* `%` in the name (for example `linux-bb.org_%.bbappend`) is a wildcard for the version.
* By convention, the `.bbappend` is placed in your layer in the same category directory as the original (for example `recipes-kernel/linux/`).

#### 2. The `FILESEXTRAPATHS` variable

When a `.bbappend` adds local files (patches, config fragments, scripts), BitBake must know where to find them. The first line of the append extends the search path:

```
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"
```

* **`:prepend`** puts the `files/` directory of your layer at the start of the `FILESPATH` search list, so BitBake finds your files before the original ones.
* **`:=`** expands `${THISDIR}` immediately, so it points to the directory of the `.bbappend`, not of the original recipe.

---

### Step 1. Find the active kernel recipe

Before writing the append, find the exact name of the recipe that provides `virtual/kernel`:

```
cd ~/Documents/Beagley_ai/yocto-labs/poky/build
bitbake-getvar -r virtual/kernel PN
```

For the BeagleY-AI with meta-beagle the result is `PN="linux-bb.org"` (the BeagleBoard.org kernel 6.12). We use the name `linux-bb.org_%.bbappend`, so the file matches any version of this recipe.

---

### Step 2. Create the directory structure in `meta-beagley-labs`

Go to the layer and create the kernel recipe directory with a `files/` subdirectory:

```
cd ~/Documents/Beagley_ai/yocto-labs/poky/meta-beagley-labs
mkdir -p recipes-kernel/linux/files
cd recipes-kernel/linux
```

The result:

```
meta-beagley-labs/
└── recipes-kernel/
    └── linux/
        ├── files/
        │   └── enable-extra-options.cfg
        └── linux-bb.org_%.bbappend
```

---

### Step 3. Write a configuration fragment (`files/enable-extra-options.cfg`)

You do not have to replace the huge `defconfig`. A small `.cfg` file with only the `CONFIG_*` options you need is enough.

Create `files/enable-extra-options.cfg`:

```
nano files/enable-extra-options.cfg
```

Enable user-space I2C access and dynamic debug messages:

```
CONFIG_I2C_CHARDEV=y
CONFIG_DYNAMIC_DEBUG=y
```

> `CONFIG_INPUT_POLLDEV`, which older tutorials add here, no longer exists in the 6.12 kernel. `merge_config.sh` would only print a warning for it.

#### About patches

To change kernel C code or a Device Tree (`.dts` / `.dtsi`), you save a patch in `git format-patch` format into `files/` and list it in `SRC_URI`, for example `file://0001-custom-kernel-fix.patch`. `do_patch` applies it to the sources. Add the patch only when the file really exists: BitBake stops at `do_fetch` if a `file://` entry cannot be found.

---

### Step 4. Write `linux-bb.org_%.bbappend`

Create `linux-bb.org_%.bbappend`:

```
nano linux-bb.org_%.bbappend
```

Write:

```
SUMMARY = "Kernel modifications for BeagleY-AI Labs"

# Prepend the local files/ directory to the search path
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# Add our kernel config fragment to the sources
SRC_URI:append = " \
    file://enable-extra-options.cfg \
"

# meta-ti merges only the fragments listed here (see setup-defconfig.inc)
KERNEL_CONFIG_FRAGMENTS:append = " ${WORKDIR}/enable-extra-options.cfg"
```

**Why `KERNEL_CONFIG_FRAGMENTS` is needed:** in Poky's `linux-yocto`, every `.cfg` in `SRC_URI` is merged automatically. The meta-ti kernels (including `linux-bb.org`) use `meta-ti-bsp/recipes-kernel/linux/setup-defconfig.inc` instead, which calls `merge_config.sh` only for the files listed in `KERNEL_CONFIG_FRAGMENTS`. With only `SRC_URI`, the fragment is fetched but silently ignored.

---

### Step 5. Check that BitBake applies the append

Go back to the build directory and check that BitBake found your `.bbappend`:

```
cd ~/Documents/Beagley_ai/yocto-labs/poky/build
bitbake-layers show-appends
```

Find the kernel section in the output:

```
linux-bb.org_6.12.bb:
  /home/alexandr/Documents/Beagley_ai/yocto-labs/poky/meta-beagley-labs/recipes-kernel/linux/linux-bb.org_%.bbappend
```

If your `.bbappend` is listed, BitBake has linked it to the original recipe.

---

### Step 6. Rebuild the Linux kernel

Build only the kernel:

```
bitbake virtual/kernel
```

**What happens during this build:**

1. **`do_fetch` / `do_unpack`**: BitBake fetches and unpacks the kernel sources and copies `enable-extra-options.cfg` into `${WORKDIR}`.
2. **`do_patch`**: patches from `SRC_URI` are applied (if any).
3. **`do_configure`**: the base `defconfig` is merged with the files from `KERNEL_CONFIG_FRAGMENTS` into the final `.config`.
4. **`do_compile`**: the kernel and the Device Tree blobs (`.dtb`) are cross-compiled.

---

### Step 7. Check the configuration result

Check that the options from the `.cfg` are in the final kernel `.config`:

```
grep -E "CONFIG_I2C_CHARDEV|CONFIG_DYNAMIC_DEBUG" tmp/work/beagley_ai-poky-linux/linux-bb.org/*/build/.config
```

Expected output:

```
CONFIG_I2C_CHARDEV=y
CONFIG_DYNAMIC_DEBUG=y
```

> These two options are already enabled in `bb.org_defconfig`, so this check alone does not prove the fragment was merged. To be sure, look for the fragment in `temp/log.do_configure` (the `merge_config.sh` output), or add an option that is disabled by default and check it.

---

🎯 You have extended the kernel recipe without touching meta-ti, added a configuration fragment and learned how meta-ti decides which fragments are merged.
