# AGENTS.md

Context for AI agents and new contributors working on this repository.

## What this is

`meta-beagley-ai` is a custom Yocto layer (**scarthgap**, Yocto 5.0) for the BeagleY-AI board
(TI AM67A / J722S). The repository is both the layer and the kas project (`kas-project.yml`).
It is a learning project: a minimal systemd image plus custom recipes, bbappends and configs.

A detailed description of the contents is in `README.md`. Keep it up to date whenever
recipes, machines or dependencies change. `README.md` describes the layer only — do not put
known issues or TODOs there; track them in this file.

## Layout

- Tracked by git: `conf/`, `recipes-*/`, `kas-project*.yml`, `README.md`, `AGENTS.md`, `COPYING.MIT`.
- **Not tracked** (`.gitignore`) and must not be edited or committed:
  `build/`, `poky/`, `meta-openembedded/`, `meta-arm/`, `meta-ti/` — these are cloned by kas.
  Reading them for reference is fine and encouraged (e.g. `meta-ti/meta-beagle/conf/machine/beagley-ai.conf`,
  `meta-ti/meta-ti-bsp/conf/machine/include/k3.inc`, `meta-ti/meta-ti-bsp/conf/multiconfig/k3r5.conf`).
- Builds are usually run via `kas-container`: inside the container the repository is
  mounted as `/repo` and the work directory is `/work` (which is why `build/conf/bblayers.conf`
  and the logs contain paths like `/work/build/...` and `${TOPDIR}/../../repo`).

## Key names

| What | Value |
|---|---|
| Layer collection | `meta-beagley-ai` (priority 6) |
| MACHINE | `beagley-custom` (inherits `beagley-ai`) |
| Companion R5 machine | `beagley-custom-k3r5` — **required**, the meta-ti `k3r5` multiconfig looks up `${MACHINE}-k3r5` |
| DISTRO | `beagley-distro` (poky + systemd, usrmerge, pam) |
| Image (kas target) | `beagley-ai-image` (core-image-minimal + dropbear, openssh-sftp-server, kernel-modules, ninvaders) |
| Kernel | `linux-bb.org` 6.12 (bbappend `linux-bb.org_%.bbappend`, fragment `enable-extra-options.cfg`) |
| Bootloader | U-Boot `bb.org` 2025.10, SD layout `sdimage-2part-efi.wks.in` (meta-ti default, GRUB EFI) |

## Build directories

| Path | Content |
|---|---|
| `build/deploy-ti/images/beagley-custom/` | **all deployed artifacts** (images, bootloaders, kernel, DTBs) — meta-ti sets `DEPLOY_DIR_IMAGE` to `TI_COMMON_DEPLOY` |
| `build/tmp/` | A53 build tree (`work/`, `log/`, `deploy/{rpm,licenses,spdx}`); there is no `tmp/deploy/images` |
| `build/tmp-k3r5/` | R5 multiconfig build tree |
| `build/downloads/`, `build/sstate-cache/` | caches shared between builds |

## Commands

```sh
kas-container build kas-project.yml                              # full image
kas-container shell kas-project.yml -c "bitbake <recipe>"         # single recipe
kas-container shell kas-project.yml -c "bitbake -e <recipe> | grep ^VAR="   # inspect a variable
kas-container shell kas-project.yml -c "bitbake-layers show-appends"
kas lock --update kas-project.yml                                 # refresh kas-project.lock.yml
```

A full build from scratch takes hours and tens of GB. Do not start one unless the user asks;
to validate changes prefer `bitbake <recipe>` or `bitbake -p` (parse only).

Logs: `build/tmp/log/cooker/beagley-custom/console-latest.log`; task logs in
`build/tmp/work/<arch>/<recipe>/<ver>/temp/log.do_*`. To check what got into the image,
read `build/deploy-ti/images/beagley-custom/beagley-ai-image-beagley-custom.rootfs.manifest`.

## Conventions

- Use only the new override syntax (`:append`, `:prepend`, `:remove`), as required by scarthgap.
- `:append` to lists needs a leading space (`" foo"`).
- New recipes go in `recipes-<category>/<name>/<name>_<version>.bb`, local files in `<name>/` or `files/`.
  `layer.conf` only picks up the `recipes-*/*/*.bb(append)` depth.
- Changes to other layers are made via `.bbappend` here, never by editing `meta-ti/` or `poky/`.
- Source patches use the `git format-patch` format (an `Upstream-Status:` header is preferred).
- When adding a recipe: `LICENSE` + `LIC_FILES_CHKSUM`, `SRC_URI[sha256sum]`.
- When changing a dependency refspec, update `kas-project.lock.yml` and the table in `README.md`.
- When renaming the image, machine or distro, update `kas-project.yml`, `README.md` and this file.
- Everything in the repository (comments in recipes/configs, documentation, commit messages) is written in English.

## Known issues

None currently. Add items here (not in `README.md`) when you find problems, and remove them once fixed.

## Git

- Main branch is `main`. Commit only when the user asks.
- Never commit `build/` or the cloned layers.
