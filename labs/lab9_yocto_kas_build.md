In this lab we move all our work out of the `poky` tree into a **separate Git repository** and automate the build with **kas** (by Siemens).

---

# Lab No. 9: Reproducible builds with kas

### Objectives

1. Move the `meta-beagley-labs` layer out of `poky/` into its own Git repository.
2. Create a custom distribution `beagley-distro` instead of the test distribution Poky.
3. Describe the whole project (layers, branches, machine, distro, target) in a `kas-project.yml` manifest.
4. Build the image with one command.

---

### Step 1. Move the layer into its own Git repository

The `meta-beagley-labs` layer is currently inside `poky/`, so the Poky Git repository either ignores it or shows it as untracked files.

1. **Move the layer directory out** (to the same level as `poky`):

   ```
   cd ~/Documents/Beagley_ai/yocto-labs
   mv poky/meta-beagley-labs ./meta-beagley-labs
   ```

2. **Initialize a Git repository for the layer:**

   ```
   cd ~/Documents/Beagley_ai/yocto-labs/meta-beagley-labs
   git init
   git add .
   git commit -m "Initial commit: custom BSP layer for BeagleY-AI"
   ```

All your recipes, machine configurations and `.bbappend` files now have their own clean commit history.

---

### Step 2. Check the layer structure and add a distro

Make sure `meta-beagley-labs` contains **all the artifacts we created**:

```
meta-beagley-labs/
├── conf/
│   ├── layer.conf
│   ├── machine/
│   │   ├── beagley-custom.conf
│   │   └── beagley-custom-k3r5.conf
│   └── distro/
│       └── beagley-distro.conf       <-- your distribution configuration
├── recipes-core/
│   └── images/
│       └── beagley-lab-image.bb      <-- your image recipe
├── recipes-games/
│   └── ninvaders/                    <-- the game recipe with patches
└── recipes-kernel/
    └── linux/
        ├── linux-bb.org_%.bbappend   <-- kernel configuration
        └── files/
            └── enable-extra-options.cfg
```

#### Create `conf/distro/beagley-distro.conf`

To stop depending on the Poky test distribution, create your own distribution file:

```
# Inherit the base Poky settings
require conf/distro/poky.conf

# meta-beagley-labs/conf/distro/beagley-distro.conf
DISTRO = "beagley-distro"
DISTRO_NAME = "BeagleY-AI Custom Lab Distribution"
DISTRO_VERSION = "1.0"

# Enable the required system managers and features
INIT_MANAGER = "systemd"
DISTRO_FEATURES:append = " pam"
```

* **The order matters.** `poky.conf` sets `DISTRO`, `DISTRO_NAME` and `DISTRO_VERSION` itself. Put `require` first, so that your values override the Poky ones and not the other way round.
* `INIT_MANAGER = "systemd"` enables `systemd` and `usrmerge` in `DISTRO_FEATURES` and makes systemd the init manager. You do not have to set these by hand.

---

### Step 3. Install kas

`kas` is a tool for reproducible, declarative Yocto builds. Install it on the host:

```
pip3 install --user kas
```

`kas-container` (installed with kas) runs the same build inside a container with all Yocto host dependencies, so the host needs only Docker or Podman.

---

### Step 4. Write the `kas-project.yml` manifest

Create `kas-project.yml` in the root of your `meta-beagley-labs` repository:

```
nano ~/Documents/Beagley_ai/yocto-labs/meta-beagley-labs/kas-project.yml
```

Write:

```
header:
  version: 14

# Our machine and our distribution
machine: beagley-custom
distro: beagley-distro

# The image built by default
target:
  - beagley-lab-image

# All layer repositories and their branches
repos:
  # The current repository with our layer
  meta-beagley-labs:

  # Official Poky repository (scarthgap branch)
  poky:
    url: "https://git.yoctoproject.org/poky"
    refspec: "scarthgap"
    layers:
      meta:
      meta-poky:
      meta-yocto-bsp:

  # Base OpenEmbedded layers
  meta-openembedded:
    url: "https://git.openembedded.org/meta-openembedded"
    refspec: "scarthgap"
    layers:
      meta-oe:

  # ARM and Texas Instruments BSP layers
  meta-arm:
    url: "https://git.yoctoproject.org/meta-arm"
    refspec: "scarthgap"
    layers:
      meta-arm:
      meta-arm-toolchain:

  meta-ti:
    url: "https://git.yoctoproject.org/meta-ti"
    refspec: "scarthgap"
    layers:
      meta-ti-bsp:
      meta-beagle:
```

* `meta-beagle` is required: it contains the `beagley-ai` machine that `beagley-custom` inherits.
* A repository without `url` (`meta-beagley-labs:`) means "the repository that contains this file".

---

### Step 5. Build with one command

To set up and build the project on **any** computer or CI server, you no longer need to clone five repositories by hand or edit `bblayers.conf` and `local.conf`. Run:

```
cd ~/Documents/Beagley_ai/yocto-labs/meta-beagley-labs
kas build kas-project.yml
# or inside a container
kas-container build kas-project.yml
```

**What kas does automatically:**

1. Clones the required versions of all repositories (`poky`, `meta-ti`, `meta-openembedded`, ...) into the working directory, which here is the root of your repository.
2. Generates `build/conf/bblayers.conf` and `build/conf/local.conf`.
3. Builds the `beagley-lab-image` image.

Add the cloned repositories and the build directory to `.gitignore`, so they are never committed:

```
build/
poky/
meta-openembedded/
meta-arm/
meta-ti/
```

To pin the exact commits of all dependencies for reproducible builds, generate a lock file next to the project file. kas uses it automatically:

```
kas lock --update kas-project.yml
```

---

🎯 Your build is now isolated, fully version-controlled in Git and runs with a single command.

> Later the repository was renamed to `meta-beagley-ai` and the image to `beagley-ai-image`. Lab 10 uses the new names. See `README.md` for the current layout.
