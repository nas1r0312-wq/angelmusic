# Arch Linux

```bash
cd desktop/arch
makepkg -si
```

This installs the release AppImage under `/opt/angelmusic` and adds an `angelmusic` command and a menu entry. It is unpacked during the build, so FUSE is not needed. Start it with `angelmusic` from a terminal or from the application menu.
