#pragma once
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Opaque handle del dispositiu
typedef struct pm_device pm_device;

// Config bàsica de la matriu
typedef struct {
  int width;            // p.ex. 64
  int height;           // p.ex. 64
  int n_addr_lines;     // p.ex. 5 (A..E)
  int lanes;            // 1 o 2
  int brightness_0_255; // 0..255
  int fps_cap;          // 0 = sense límit
} pm_config;

// ── API ───────────────────────────────────────────────────────────────────────
pm_device* pm_open(const pm_config* cfg);
void       pm_close(pm_device* dev);

int        pm_set_brightness(pm_device* dev, int value_0_255);
int        pm_swap_buffers(pm_device* dev);

uint8_t*   pm_map_framebuffer(pm_device* dev,
                              int* out_w, int* out_h,
                              int* out_stride_bytes, int* out_bpp);

int        pm_put_pixel(pm_device* dev, int x, int y,
                        uint8_t r, uint8_t g, uint8_t b);

#ifdef __cplusplus
} // extern "C"
#endif
