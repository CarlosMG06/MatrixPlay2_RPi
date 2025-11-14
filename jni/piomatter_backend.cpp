#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <span>
#include <stdexcept>
#include <vector>

#include <algorithm>  // per std::max
extern "C" {
  #include <math.h>   // posa ::pow i ::round a l’espai global
}

extern "C" {
#include "piomatter_abi.h"  // el teu ABI (C)
}

#include "piomatter/pins.h"
#include "piomatter/matrixmap.h"
#include "piomatter/render.h"
#include "piomatter/piomatter.h"

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

static std::vector<int> build_interleave_columns_map(int W, int H, int LANES) {
    // idèntic a map_interleave_columns_no_transform del Python
    int lane_w      = W / LANES;
    int lane_pixels = (W * H) / LANES;
    std::vector<int> map;
    map.resize(W * H);
    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            int lane = x % LANES;
            int xl   = x / LANES;
            int phys = lane * lane_pixels + y * lane_w + xl;
            map[y * W + x] = phys;
        }
    }
    return map;
}

// ─────────────────────────────────────────────────────────────────────────────
// Opaque device que manté el core PioMatter + framebuffer Java (RGB888 packed)
// ─────────────────────────────────────────────────────────────────────────────

struct pm_device {
    pm_config cfg{};
    int stride = 0;
    int bpp    = 24;

    // Framebuffer que exposarem a Java via ByteBuffer directe
    std::vector<uint8_t> fb; // H * W * 3

    // Geometry i motor PioMatter (pinout Active3, colors RGB888 packed)
    std::unique_ptr<piomatter::matrix_geometry> geom;
    std::unique_ptr<piomatter::piomatter<piomatter::active3_pinout,
                                         piomatter::colorspace_rgb888_packed>>
        dev;
};

// ─────────────────────────────────────────────────────────────────────────────
// ABI C (impl real)
// ─────────────────────────────────────────────────────────────────────────────

extern "C" {

pm_device* pm_open(const pm_config* cfg_in) {
    if (!cfg_in || cfg_in->width <= 0 || cfg_in->height <= 0) return nullptr;

    auto d = new pm_device();
    d->cfg    = *cfg_in;
    d->stride = d->cfg.width * 3;
    d->fb.resize((size_t)d->cfg.height * (size_t)d->stride, 0);

    try {
        // 1) Construir el mapping (interleave columns, sense rotacions/mirroring)
        auto map = build_interleave_columns_map(d->cfg.width, d->cfg.height, d->cfg.lanes);

        // 2) Fer un schedule (10 planes, temporal=0) com al Python
        size_t pixels_across = d->cfg.width; // 1 panell de 64x64 → 64
        auto sched = piomatter::make_temporal_dither_schedule(
            /*n_planes=*/10, pixels_across, /*n_temporal_planes=*/0);

        // 3) Geometry: (pixels_across, n_addr_lines, width, height, map, lanes, schedules)
        d->geom = std::make_unique<piomatter::matrix_geometry>(
            pixels_across,
            (size_t)d->cfg.n_addr_lines,
            (size_t)d->cfg.width,
            (size_t)d->cfg.height,
            std::move(map),
            (size_t)d->cfg.lanes,
            sched
        );

        // 4) Crear el core PioMatter amb colorspace RGB888 packed i pinout Active3
        //    El constructor espera un span<const uint8_t> del framebuffer.
        std::span<const uint8_t> span_fb(d->fb.data(), d->fb.size());
        d->dev = std::make_unique<piomatter::piomatter<
            piomatter::active3_pinout,
            piomatter::colorspace_rgb888_packed>>(span_fb, *d->geom);

        // (Opcional) brightness per software: ja l’apliques a Java.
        // Si en el futur afegeixes brightness HW, guarda'l aquí i aplica'l.

    } catch (...) {
        delete d;
        return nullptr;
    }

    return d;
}

void pm_close(pm_device* dev) {
    if (!dev) return;
    try {
        dev->dev.reset();
        dev->geom.reset();
    } catch (...) {
        // ignore
    }
    delete dev;
}

int pm_set_brightness(pm_device* dev, int value_0_255) {
    if (!dev) return -1;
    // No hi ha setter HW al core; guardem el valor (el Java ja escala en SW)
    if (value_0_255 < 0) value_0_255 = 0;
    if (value_0_255 > 255) value_0_255 = 255;
    dev->cfg.brightness_0_255 = value_0_255;
    return 0;
}

int pm_swap_buffers(pm_device* dev) {
    if (!dev || !dev->dev) return -1;
    // Envia el framebuffer actual al maquinari
    // (piomatter::piomatter::show() fa la conversió i el blit via el seu thread)
    return dev->dev->show();
}

uint8_t* pm_map_framebuffer(pm_device* dev,
                            int* out_w, int* out_h,
                            int* out_stride_bytes, int* out_bpp) {
    if (!dev) return nullptr;
    if (out_w) *out_w = dev->cfg.width;
    if (out_h) *out_h = dev->cfg.height;
    if (out_stride_bytes) *out_stride_bytes = dev->stride;
    if (out_bpp) *out_bpp = dev->bpp;
    return dev->fb.data();
}

int pm_put_pixel(pm_device* dev, int x, int y, uint8_t r, uint8_t g, uint8_t b) {
    if (!dev) return -1;
    if (x < 0 || y < 0 || x >= dev->cfg.width || y >= dev->cfg.height) return -2;
    size_t off = (size_t)y * (size_t)dev->stride + (size_t)x * 3u;
    dev->fb[off + 0] = r;
    dev->fb[off + 1] = g;
    dev->fb[off + 2] = b;
    return 0;
}

} // extern "C"
