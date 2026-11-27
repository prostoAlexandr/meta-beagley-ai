In this lab we add third-party software to the Yocto Project from scratch. We write a BitBake recipe for the console game nInvaders, go through the main recipe variables, solve typical cross-compilation problems of old C code on 64-bit ARM (AArch64) and run the game on the BeagleY-AI.

---

# Lab No. 3: Writing a custom recipe for a C application (nInvaders)

### Objectives

1. Understand the recipe file naming (`${PN}_${PV}.bb`) and syntax.
2. Use the recipe metadata variables (`SUMMARY`, `LICENSE`, `LIC_FILES_CHKSUM`, `SRC_URI`, `DEPENDS`).
3. Obtain the source and license checksums.
4. Fix old C code for modern GCC during cross-compilation (`-std=gnu17`, `-fcommon`).
5. Build and install a project that uses a plain Makefile.
6. Add `ninvaders` to the image, update the NFS root and test the game on the BeagleY-AI.

---

### Theoretical introduction: anatomy of a BitBake recipe

A recipe is a `.bb` file with instructions for BitBake: where to download the source code, which dependencies are needed to build it, how to configure and compile it, and where to install the resulting files.

#### File naming

A recipe file is named `<package name>_<version>.bb`, for example `ninvaders_0.1.1.bb`:

* `${PN}` (package name) = `ninvaders`
* `${PV}` (package version) = `0.1.1`

#### Key recipe variables

* **`SUMMARY` / `DESCRIPTION`**: short and full description of the program for the package manager.
* **`HOMEPAGE`**: URL of the project home page.
* **`SECTION`**: software category, for example `console/games` or `utils`.
* **`LICENSE`**: source code license as an SPDX identifier (`GPL-2.0-only`, `MIT`, `Apache-2.0`).
* **`LIC_FILES_CHKSUM`**: a file in the sources that contains the license text and its MD5 checksum. BitBake uses it to detect when the author changes the license terms.
* **`SRC_URI`**: where to fetch the sources (`http://`, `https://`, `git://`) and local files (`file://`).
* **`DEPENDS`**: build-time dependencies. Their libraries and headers are built first and placed in the recipe's `recipe-sysroot`.

---

### Step 1. Analyze the nInvaders source code

nInvaders is a Space Invaders clone for the Linux terminal, written in C.

* **Library dependencies:** nInvaders draws its character graphics with **ncurses**, so the recipe needs `DEPENDS = "ncurses"`.
* **Build system:** there is no Autotools (`configure.ac`) or CMake (`CMakeLists.txt`), only a plain static `Makefile`.
* **License:** GPLv2 (`GPL-2.0-only`). The full text is in `gpl.txt`, and every source file starts with a license header.

---

### Step 2. Create the recipe directory

Yocto recipes are grouped in directories by category (`recipes-extended`, `recipes-core`, `recipes-support`).

*(Note: in this lab we put the recipe directly into the Poky tree. In Lab 4 we move it into our own layer `meta-beagley-labs`.)*

Go to Poky and create the recipe directory:

```
cd ~/Documents/Beagley_ai/yocto-labs/poky
mkdir -p meta/recipes-extended/ninvaders
cd meta/recipes-extended/ninvaders
```

---

### Step 3. Write `ninvaders_0.1.1.bb`

Create `ninvaders_0.1.1.bb` (`nano ninvaders_0.1.1.bb`):

```
SUMMARY = "Space Invaders clone in ncurses"
DESCRIPTION = "nInvaders is a terminal-based game inspired by Space Invaders, written in C using the ncurses library."
HOMEPAGE = "https://ninvaders.sourceforge.net/"
SECTION = "console/games"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://nInvaders.c;md5=d3b22b9996b86202cdc58487fc6ad8e1"

SRC_URI = "${SOURCEFORGE_MIRROR}/ninvaders/ninvaders-${PV}.tar.gz"
SRC_URI[sha256sum] = "bfbc5c378704d9cf5e7fed288dac88859149bee5ed0850175759d310b61fd30b"

DEPENDS = "ncurses"

# Compiler flags for compatibility with C23 / GCC 10+
CFLAGS:append = " -std=gnu17 -fcommon"

# Pass the cross compiler and BitBake variables to the Makefile
EXTRA_OEMAKE = 'CC="${CC}" CFLAGS="${CFLAGS}" LDFLAGS="${LDFLAGS}"'

do_install() {
    install -d ${D}${bindir}
    install -m 0755 nInvaders ${D}${bindir}/ninvaders
}
```

---

### Step 4. Build problems and their solutions

A naive recipe without the extra settings above fails with several errors. Here is what causes them.

#### 1. Wrong or missing checksums (`LIC_FILES_CHKSUM`, `SRC_URI[sha256sum]`)

* **Problem:** BitBake stops the build if a checksum is wrong or missing.
* **How to get the values:** run `bitbake ninvaders` with placeholder values. BitBake prints the actual `sha256sum` of the downloaded archive and the `md5` of the license file, and you copy them into the recipe.

#### 2. Conflicting function types under C23 (`conflicting types for 'doSleep'`)

* **Log:** `nInvaders.c: error: conflicting types for 'doSleep'; have 'void(void)'`
* **Cause:** the code was written for old C standards. In C23 (the default in modern GCC), a declaration `void doSleep()` means "a function without arguments", but the program calls it with a time argument (`doSleep(int)`).
* **Fix:** the compiler flag `-std=gnu17` switches back to the C17 dialect.

#### 3. Multiple symbol definitions (`multiple definition of 'skill_level'`)

* **Log:** `player.o:(.bss+0x0): multiple definition of 'skill_level'; aliens.o:(.bss+0x674): first defined here`
* **Cause:** global variables are defined in header files (`.h`) without `extern`. Since GCC 10, `-fno-common` is the default, so the linker no longer merges identical global symbols from several object files.
* **Fix:** the compiler flag `-fcommon` lets the linker merge uninitialized global variables again.

#### 4. Compile and install

* **`EXTRA_OEMAKE`:** the default `do_compile` runs `oe_runmake`, which calls `make` with `EXTRA_OEMAKE`. The original Makefile hard-codes `gcc` and its own `CFLAGS`, so we pass the cross compiler (`aarch64-poky-linux-gcc` with the right sysroot) and our flags on the `make` command line, where they take precedence over the Makefile.
* **`do_install`:** installs the `nInvaders` binary into `${D}${bindir}`, which becomes `/usr/bin/ninvaders` on the board.
  * `${D}`: the destination directory of the package.
  * `${bindir}`: the system binary directory (`/usr/bin`).

---

### Step 5. Build the recipe and add it to the image

#### 5.1. Build the recipe alone

Go to the build directory and build only `ninvaders`:

```
cd ~/Documents/Beagley_ai/yocto-labs/poky/build
bitbake ninvaders
```

BitBake downloads `ninvaders-0.1.1.tar.gz`, verifies the checksums, unpacks the sources, prepares a `recipe-sysroot` with the ncurses headers, compiles the binary and creates the package (`.rpm` with the default Poky `PACKAGE_CLASSES`).

#### 5.2. Add `ninvaders` to the image

Open `conf/local.conf` and add `ninvaders` to the installed packages:

```
IMAGE_INSTALL:append = " ninvaders"
```

#### 5.3. Rebuild `core-image-minimal`

```
bitbake core-image-minimal
```

---

### Step 6. Update the NFS root

In Lab 2 we set up the rootfs over NFS, so the SD card does not need to be reflashed.

Extract the new rootfs tarball into `/nfs`:

```
sudo tar xpf deploy-ti/images/beagley-ai/core-image-minimal-beagley-ai.rootfs.tar.xz -C /nfs
```

Check that the game binary is there:

```
ls -la /nfs/usr/bin/ninvaders
```

---

### Step 7. Run the game on the BeagleY-AI

1. Make sure the board is connected over Ethernet and booted from NFS.
2. Connect to the board with SSH:

   ```
   ssh root@10.42.0.100
   ```

3. Start the game:

   ```
   ninvaders
   ```

The Space Invaders character interface opens in your terminal.

**Controls:** Left/Right arrows to move, Space to fire, `p` to pause, `q` to quit.

---

### Step 8. Inspect the build results in `tmp/work/`

Go to the work directory of the recipe:

```
cd tmp/work/aarch64-poky-linux/ninvaders/0.1.1/
ls -la
```

Look at the key directories:

* **`ninvaders-0.1.1/`** (`${S}`): the unpacked source code.
* **`recipe-sysroot/`**: the isolated headers and libraries. Find `usr/include/ncurses.h` there.
* **`image/`** (`${D}`): the package file tree with `usr/bin/ninvaders`.
* **`temp/`**: open `log.do_compile` and find the real `aarch64-poky-linux-gcc` command line with `-std=gnu17 -fcommon`.

---

🎯 You have written a recipe from scratch, fixed the build of old C code for a modern cross compiler and run your own package on the BeagleY-AI.
