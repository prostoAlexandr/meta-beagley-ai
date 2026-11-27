In this lab we stop modifying the Poky tree and create our own layer, `meta-beagley-labs`. We move the nInvaders recipe into it and replace the manual `IMAGE_INSTALL:append` lines in `local.conf` with our own image recipe.

---

# Lab No. 4: Creating a Yocto layer

### Objectives

1. Use `bitbake-layers` to create and manage layers.
2. Study the layer configuration file `conf/layer.conf` (`BBFILE_COLLECTIONS`, `BBFILE_PRIORITY`, `LAYERSERIES_COMPAT`).
3. Move the `ninvaders` recipe from `poky/meta/` to `meta-beagley-labs`.
4. Create a custom image recipe `beagley-lab-image.bb` based on `core-image-minimal`.
5. Remove the manual `IMAGE_INSTALL:append` lines from `conf/local.conf` and build the image from our layer.
6. Test the new image on the BeagleY-AI.

---

### Step 1. Create a new layer with `bitbake-layers`

A layer skeleton is created with `bitbake-layers create-layer`.

1. Go to the project root (one level above `build`):

   ```
   cd ~/Documents/Beagley_ai/yocto-labs/poky
   ```

2. Generate a new layer called `meta-beagley-labs`:

   ```
   bitbake-layers create-layer meta-beagley-labs
   ```

3. Look at the created structure:

   ```
   tree meta-beagley-labs/
   ```

The tool creates:

* **`conf/layer.conf`**: the main layer configuration file.
* **`recipes-example/example/example_0.1.bb`**: a template recipe (can be deleted right away).
* **`COPYING.MIT`** and **`README`**: the license and the description of the layer.

---

### Step 2. Study `meta-beagley-labs/conf/layer.conf`

Open `meta-beagley-labs/conf/layer.conf`:

```
# Add the recipes (.bb) and append files (.bbappend) to BBFILES
BBFILES += "${LAYERDIR}/recipes-*/*/*.bb \
            ${LAYERDIR}/recipes-*/*/*.bbappend"

BBFILE_COLLECTIONS += "meta-beagley-labs"
BBFILE_PATTERN_meta-beagley-labs = "^${LAYERDIR}/"
BBFILE_PRIORITY_meta-beagley-labs = "6"

LAYERVERSION_meta-beagley-labs = "1"
LAYERSERIES_COMPAT_meta-beagley-labs = "scarthgap"
```

**Key variables:**

* **`BBFILES`**: patterns for the recipes (`.bb`) and append files (`.bbappend`) of the layer. Note the `recipes-*/*/*.bb` depth: recipes must be exactly two directories below the layer root.
* **`BBFILE_COLLECTIONS`**: the unique internal name of the layer for BitBake.
* **`BBFILE_PRIORITY`**: the layer priority. When several layers provide the same recipe, the higher number wins. `6` is the default for a new layer.
* **`LAYERSERIES_COMPAT`**: the compatible Yocto releases (here `scarthgap`, the Yocto 5.0 LTS release).

---

### Step 3. Add the layer to the project

BitBake sees the recipes of a layer only after it is registered in `conf/bblayers.conf`.

1. Add the layer:

   ```
   cd ~/Documents/Beagley_ai/yocto-labs/poky/build
   bitbake-layers add-layer ../meta-beagley-labs
   ```

2. List all layers and their priorities:

   ```
   bitbake-layers show-layers
   ```

The output must contain `meta-beagley-labs` with priority `6`.

---

### Step 4. Move the `ninvaders` recipe into our layer

Now we remove the recipe from `poky/meta/` and put it into our own layer.

1. Create the category directory in `meta-beagley-labs`:

   ```
   mkdir -p ../meta-beagley-labs/recipes-games/ninvaders
   ```

2. Move `ninvaders_0.1.1.bb` from `meta/recipes-extended/` to the layer:

   ```
   mv ../meta/recipes-extended/ninvaders/ninvaders_0.1.1.bb ../meta-beagley-labs/recipes-games/ninvaders/
   rm -rf ../meta/recipes-extended/ninvaders
   ```

3. Delete the template example created by `create-layer`:

   ```
   rm -rf ../meta-beagley-labs/recipes-example
   ```

4. Check that BitBake finds the recipe at its new location:

   ```
   bitbake-layers show-recipes ninvaders
   ```

The output shows the recipe now comes from the `meta-beagley-labs` layer.

---

### Step 5. Create an image recipe `beagley-lab-image.bb`

By Yocto best practices, the package set of the final system should not be defined with `IMAGE_INSTALL:append` in `conf/local.conf`. Create an image recipe instead.

1. Create the image recipe directory in the layer:

   ```
   mkdir -p ../meta-beagley-labs/recipes-core/images
   ```

2. Create `beagley-lab-image.bb`:

   ```
   nano ../meta-beagley-labs/recipes-core/images/beagley-lab-image.bb
   ```

3. Write:

   ```
   SUMMARY = "Custom minimal image for BeagleY-AI labs"
   DESCRIPTION = "Minimal image based on core-image-minimal with dropbear, kernel modules and ninvaders included."
   LICENSE = "MIT"

   # Inherit all functionality of the standard minimal image
   require recipes-core/images/core-image-minimal.bb

   # Add the required packages directly in the image recipe
   IMAGE_INSTALL:append = " \
       dropbear \
       kernel-modules \
       ninvaders \
   "
   ```

---

### Step 6. Clean up `conf/local.conf` and build the new image

1. Open `conf/local.conf` and **remove** (or comment out) the `IMAGE_INSTALL:append` lines added in Labs 2 and 3. `local.conf` must not contain any image packages.
2. Build your own image:

   ```
   bitbake beagley-lab-image
   ```

---

### Step 7. Update the NFS root and test on the BeagleY-AI

1. Extract the new rootfs tarball into `/nfs`:

   ```
   sudo tar xpf deploy-ti/images/beagley-ai/beagley-lab-image-beagley-ai.rootfs.tar.xz -C /nfs
   ```

2. Connect with SSH and start the game on the board:

   ```
   ssh root@10.42.0.100
   ninvaders
   ```

---

🎯 Your recipes and the image definition now live in your own layer, and `local.conf` contains only build settings.
