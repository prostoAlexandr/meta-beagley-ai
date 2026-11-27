In this lab we look inside BitBake: the layout of the `tmp/work/` build directory, how to inspect build variables and how virtual providers work. Then we add packages to the image and boot the BeagleY-AI with its root filesystem (`rootfs`) mounted over NFS from the host.

---

# Lab No. 2: Advanced Yocto configuration, BitBake debugging and NFS root

### Objectives

1. Study the `tmp/work/` build directory and the task debugging files.
2. Inspect global and recipe variables (`bitbake-getvar`, `bitbake -e`).
3. Understand virtual providers (`virtual/kernel`, `virtual/bootloader`).
4. Add the `dropbear` SSH server and `kernel-modules` to the image through `conf/local.conf`.
5. Set up an NFS server on the host and boot the BeagleY-AI rootfs over the network.

---

### Step 1. Explore `tmp/work/` and debug tasks

When BitBake builds a recipe, it runs a chain of tasks: fetch (`do_fetch`), unpack (`do_unpack`), patch (`do_patch`), configure (`do_configure`), compile (`do_compile`) and install (`do_install`).

All intermediate files and logs of every recipe are kept under `tmp/work/`.

#### 1.1. Layout of `tmp/work/`

Go to the build directory and look at the work directories:

```
cd ~/Documents/Beagley_ai/yocto-labs/poky/build
ls -la tmp/work/beagley_ai-poky-linux/
```

Every recipe directory (for example `u-boot-bb.org` or `linux-bb.org`) contains these key directories:

* **`${WORKDIR}`**: the root work directory of the recipe.
* **`${S}`** (*sources*): the unpacked and patched source code.
* **`${B}`** (*build*): where the objects and binaries (`.o`, `.a`, `.so`) are built.
* **`${D}`** (*destination*): the file tree of the package, prepared for packaging.
* **`temp/`**: the logs and the executable scripts of every task that was run.

Only machine-specific recipes are built in `beagley_ai-poky-linux/`. Generic target packages such as `zlib` are built in `tmp/work/aarch64-poky-linux/`, and native host tools in `tmp/work/x86_64-linux/`.

#### 1.2. Task scripts and debug logs

Open the `temp/` directory of any built recipe:

```
ls -la tmp/work/aarch64-poky-linux/zlib/*/temp/
```

There is a pair of files for every task:

* **`run.do_<task>.<pid>`**: the exact shell or Python script BitBake executed for the task.
* **`log.do_<task>.<pid>`**: the full output of that task. `log.do_<task>` is a symlink to the latest run.

When a task fails, read its `log.do_<task>` first. To reproduce the failure by hand, run the matching `run.do_<task>` script.

---

### Step 2. Inspect BitBake variables

Get the final value of a variable for a recipe:

```
bitbake-getvar -r zlib S
bitbake-getvar MACHINE
```

`bitbake -e` prints the whole environment of a recipe. This includes the **history** of every assignment, with the file and line it came from:

```
bitbake -e zlib | less        # search for "^# \$S" to see how S was built
bitbake -e zlib | grep ^WORKDIR=
```

---

### Step 3. Virtual providers

A recipe can depend on a functionality instead of a specific recipe. For example, the kernel dependency is described by `virtual/kernel`. Dry-run BitBake to see which recipe provides it:

```
bitbake -vn virtual/kernel
```

For the BeagleY-AI you will see:

```
NOTE: selecting linux-bb.org to satisfy virtual/kernel due to PREFERRED_PROVIDERS
```

Try switching to another kernel only from `conf/local.conf`:

```
PREFERRED_PROVIDER_virtual/kernel:beagley-ai = "linux-dummy"
```

Run `bitbake -vn virtual/kernel` again and check that `linux-dummy` is selected. The `:beagley-ai` machine override is needed because the machine configuration already sets this variable. Remove the line afterwards to go back to `linux-bb.org`.

Useful BitBake commands:

* `bitbake -c listtasks virtual/kernel`: list the tasks of a recipe.
* `bitbake -c <task> virtual/kernel`: run one task and its dependencies (for example `menuconfig`).
* `bitbake -f -c compile virtual/kernel`: force a task to run again.
* `bitbake -s`: list the available recipes and their versions.

---

### Step 4. Add packages to the image

The `IMAGE_INSTALL` variable controls the packages included in the image. Append to it instead of overwriting it. Add to `conf/local.conf`:

```
IMAGE_INSTALL:append = " dropbear kernel-modules"
```

Rebuild the image:

```
bitbake core-image-minimal
```

---

### Step 5. Set up the NFS server on the host

Install the NFS server and create the root NFS directory:

```
sudo apt install nfs-kernel-server
sudo mkdir -m 777 /nfs
```

Export the directory by adding this line to `/etc/exports`:

```
/nfs *(rw,sync,no_root_squash,subtree_check)
```

Apply the new configuration:

```
sudo exportfs -r
```

Extract the rootfs into the NFS directory:

```
sudo tar xpf deploy-ti/images/beagley-ai/core-image-minimal-beagley-ai.rootfs.tar.xz -C /nfs
```

---

### Step 6. Configure the network and the kernel boot arguments

Connect the board to the host with an Ethernet cable. Give the host interface a static address in the `10.42.0.0/24` network (replace `enp3s0` with your interface name from `ip link`):

```
nmcli con add type ethernet ifname enp3s0 ip4 10.42.0.1/24
```

Now tell the kernel to mount its root filesystem over NFS. With the default meta-ti layout the kernel is started by GRUB, so mount the FAT `boot` partition of the SD card on the PC and edit `EFI/BOOT/grub.cfg`. Change the `linux` line to (on one line):

```
linux /Image root=/dev/nfs rw nfsroot=10.42.0.1:/nfs,nfsvers=3,tcp ip=10.42.0.100:::::eth0 rootwait
```

* `root=/dev/nfs` together with `nfsroot=` mounts the rootfs from `10.42.0.1:/nfs`.
* `ip=10.42.0.100:::::eth0` gives the board a static address on `eth0`.

The BeagleY-AI kernel already has everything this needs built in (`CONFIG_ROOT_NFS`, `CONFIG_IP_PNP` and the CPSW Ethernet driver).

> Since Lab 10 the board boots through U-Boot extlinux. Put the same arguments into the `APPEND` line of `extlinux/extlinux.conf` on the FAT partition instead.

---

### Step 7. Boot over NFS and connect with SSH

Boot the board and watch the UART console: the kernel should print `VFS: Mounted root (nfs filesystem)`. Dropbear runs as a service, so you can now log in from the host:

```
ssh root@10.42.0.100
```

From now on, after every image rebuild you only need to extract the new rootfs tarball into `/nfs`. The SD card no longer has to be reflashed.

---

🎯 You can now inspect BitBake tasks and variables, understand which recipe provides `virtual/kernel`, and run the BeagleY-AI with its rootfs served over NFS from the host.
