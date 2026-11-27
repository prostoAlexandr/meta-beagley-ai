In this lab we learn the main tool for application developers in the Yocto Project: **devtool**.

Until now every code change meant editing files by hand, cleaning the cache with `bitbake -c cleansstate` and rebuilding. `devtool` automates this cycle. You edit the source code on the host, send the built binary to the board over SSH in seconds, and when you are done devtool generates the patches for your layer.

---

# Lab No. 7: Fast development and patching cycle with devtool

### Objectives

1. Understand the temporary `workspace` layer and the main `devtool` commands.
2. Put the `ninvaders` recipe into development mode (`devtool modify`).
3. Change the C source code of the game on the host.
4. Rebuild quickly and deploy the binary to the board over SSH (`devtool deploy-target`).
5. Finish the work and generate a patch in our layer `meta-beagley-labs` (`devtool finish`).

---

### Theoretical introduction: how devtool works

`devtool` creates a separate **workspace** layer in the build directory. When a recipe is put into `devtool modify` mode:

* The package sources are extracted into a normal local directory that is a Git repository.
* BitBake builds the recipe from this directory (through the `externalsrc` class).
* You edit the code in your IDE or editor, and `devtool build` recompiles **only what changed**, in seconds.

---

### Step 1. Put the recipe into development mode

Go to the build directory and hand the `ninvaders` recipe over to `devtool`:

```
cd ~/Documents/Beagley_ai/yocto-labs/poky/build
devtool modify ninvaders
```

**What happened:**

1. The source directory `build/workspace/sources/ninvaders` was created.
2. The temporary `build/workspace` layer was added to `bblayers.conf`.

Check the workspace status:

```
devtool status
```

You will see that `ninvaders` is being modified and that its sources are in `workspace/sources/ninvaders`.

---

### Step 2. Change the C code

Go to the game sources and open the file that draws the screens:

```
cd workspace/sources/ninvaders
nano view.c
```

Find the function that draws the title screen and change the text, for example add **"BeagleY-AI Edition"** to the title.

Save the file and commit the change to the local Git repository of the sources:

```
git add view.c
git commit -m "Custom title for BeagleY-AI"
```

---

### Step 3. Rebuild quickly and deploy to the board

1. Build the modified package:

   ```
   devtool build ninvaders
   ```

   The build takes only a few seconds, because only the changed `view.c` is recompiled.

2. Send the built files directly to the running BeagleY-AI over the network, without rebuilding the image or updating the SD card or NFS root:

   ```
   devtool deploy-target ninvaders root@10.42.0.100
   ```

3. Log in to the board with SSH (or through `picocom`) and start the game:

   ```
   ssh root@10.42.0.100
   ninvaders
   ```

You see your changes in the game right away.

---

### Step 4. Save the changes to `meta-beagley-labs` (`devtool finish`)

When the code works on the hardware, save the changes back to our permanent layer.

1. Remove the temporarily deployed files from the board:

   ```
   devtool undeploy-target ninvaders root@10.42.0.100
   ```

2. Export the changes from the workspace into our layer:

   ```
   devtool finish ninvaders ../meta-beagley-labs
   ```

**What `devtool finish` does:**

* Creates a patch file from your Git commit (`0001-Custom-title-for-BeagleY-AI.patch`).
* Puts the patch next to the recipe. The recipe has no `files/` directory yet, so devtool creates one named after the recipe: `meta-beagley-labs/recipes-games/ninvaders/ninvaders/`.
* Adds `file://0001-Custom-title-for-BeagleY-AI.patch` to `SRC_URI` in `ninvaders_0.1.1.bb`. The recipe is already in the target layer, so devtool edits it directly. For a recipe from another layer it would create a `.bbappend` instead.
* Removes the recipe from the workspace.

---

### Step 5. Check your layer

Look at the recipe directory in your layer:

```
tree ../meta-beagley-labs/recipes-games/ninvaders/
```

You will see the generated patch and the updated recipe.

Rebuild the image to make the change permanent on the board:

```
bitbake beagley-lab-image
```

---

🎯 You can now change the source code of any recipe, test it on the board within seconds and turn the result into a clean patch in your layer.
