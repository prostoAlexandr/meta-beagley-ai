In this lab we create and use a standalone **SDK** (Software Development Kit).

`devtool` is mainly a tool for the distribution engineer, while an SDK is made for application developers. They do not need BitBake, the Yocto sources or the build infrastructure. A single self-extracting installer gives them a cross compiler, headers and libraries that exactly match the root filesystem of the BeagleY-AI.

---

# Lab No. 8: Generating and using the Poky SDK

### Objectives

1. Understand the difference between the generic toolchain (`meta-toolchain`) and an image-based SDK (`populate_sdk`).
2. Generate an SDK installer for our custom image `beagley-lab-image`.
3. Install the SDK on the host and look at its `sysroots` directory.
4. Set up the environment variables (`$CC`, `$CFLAGS`, `$LDFLAGS`) with the `environment-setup` script.
5. Compile a standalone C application from the host terminal, without `bitbake`.
6. Copy the binary to the BeagleY-AI and run it.

---

### Theoretical introduction: `meta-toolchain` vs `populate_sdk`

* **`bitbake meta-toolchain`** builds a generic toolchain: the cross compiler plus a basic set of libraries. It does not know what is installed in your image.
* **`bitbake -c populate_sdk <image>`** builds an SDK whose target sysroot contains the libraries and headers of all packages in `<image>`. An application built with it links against exactly what is on the board.

---

### Step 1. Generate the SDK for the custom image

The `populate_sdk` task builds an SDK whose libraries match `beagley-lab-image`.

1. Go to the build directory:

   ```
   cd ~/Documents/Beagley_ai/yocto-labs/poky/build
   ```

2. Generate the SDK:

   ```
   bitbake -c populate_sdk beagley-lab-image
   ```

   This can take 10 to 15 minutes: BitBake builds the native host tools and packs the whole target sysroot.

The installer script is created in `tmp/deploy/sdk/`. Unlike images, the SDK is not redirected to `deploy-ti/`.

---

### Step 2. Install the SDK on the host

The SDK is installed by running the generated `.sh` script.

1. Create the installation directory:

   ```
   mkdir -p ~/Documents/Beagley_ai/yocto-labs/sdk
   ```

2. Run the installer from `tmp/deploy/sdk/`:

   ```
   tmp/deploy/sdk/poky-glibc-x86_64-beagley-lab-image-aarch64-beagley-custom-toolchain-*.sh
   ```

3. When the installer asks for the installation path, enter the absolute path of the created directory:

   ```
   Enter target directory for SDK (default: /opt/poky/5.0.21): /home/alexandr/Documents/Beagley_ai/yocto-labs/sdk
   ```

   Confirm with `Y`.

The installed SDK contains `environment-setup-aarch64-poky-linux`, `version-aarch64-poky-linux` and the `sysroots/` directory with two sysroots:

* `sysroots/aarch64-poky-linux/`: the target libraries and headers (same as on the board);
* `sysroots/x86_64-pokysdk-linux/`: the host tools, including the cross compiler.

---

### Step 3. Set up and check the SDK environment

Before working with the SDK, load its environment variables in every new shell.

1. Open a **new terminal**, so that the BitBake environment from `oe-init-build-env` is **not** loaded.
2. Go to the SDK directory and run `source`:

   ```
   cd ~/Documents/Beagley_ai/yocto-labs/sdk
   source environment-setup-aarch64-poky-linux
   ```

3. Check the cross compiler variable:

   ```
   echo $CC
   ```

You will see `aarch64-poky-linux-gcc` with the machine flags and `--sysroot` pointing to `sysroots/aarch64-poky-linux`.

---

### Step 4. Compile a C application outside Yocto

Now write a C program and compile it directly from the console.

1. Create a separate directory:

   ```
   mkdir -p ~/Documents/Beagley_ai/yocto-labs/app_test && cd ~/Documents/Beagley_ai/yocto-labs/app_test
   ```

2. Create `hello_sdk.c`:

   ```
   #include <stdio.h>

   int main(void) {
       printf("Hello from standalone SDK on BeagleY-AI!\n");
       return 0;
   }
   ```

3. Compile it with the `$CC` variable from the SDK:

   ```
   $CC hello_sdk.c -o hello_sdk
   ```

4. Check the type and architecture of the binary:

   ```
   file hello_sdk
   ```

   The output must contain `ARM aarch64, 64-bit` and `dynamically linked`.

---

### Step 5. Run it on the BeagleY-AI

1. Copy the binary to the board with `scp`:

   ```
   scp hello_sdk root@10.42.0.100:/usr/bin/
   ```

   > Since OpenSSH 9.0, `scp` uses the SFTP protocol by default. Dropbear has no SFTP server, so either add `openssh-sftp-server` to the image or use the legacy protocol: `scp -O hello_sdk root@10.42.0.100:/usr/bin/`.

2. Log in to the board with SSH and run the program:

   ```
   ssh root@10.42.0.100
   hello_sdk
   ```

The console prints `Hello from standalone SDK on BeagleY-AI!`.

---

🎯 You have built an SDK that matches your image and compiled an application for the BeagleY-AI without BitBake.
