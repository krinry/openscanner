#include "native_scanner.h"

Quad detectDocumentBounds(const uint8_t* grayBuffer, int width, int height) {
    // Default fallback: 85% inner rectangle
    float marginX = width * 0.08f;
    float marginY = height * 0.08f;
    Quad fallback = {
        {marginX, marginY},
        {width - marginX, marginY},
        {width - marginX, height - marginY},
        {marginX, height - marginY}
    };

    if (!grayBuffer || width < 50 || height < 50) {
        return fallback;
    }

    // Work on downsampled grid for high-speed edge detection
    int downsample = 4;
    int sw = width / downsample;
    int sh = height / downsample;

    std::vector<uint8_t> small(sw * sh);
    for (int y = 0; y < sh; ++y) {
        for (int x = 0; x < sw; ++x) {
            small[y * sw + x] = grayBuffer[(y * downsample) * width + (x * downsample)];
        }
    }

    // Sobel edge gradient computation
    std::vector<int> grad(sw * sh, 0);
    int maxGrad = 0;
    for (int y = 1; y < sh - 1; ++y) {
        for (int x = 1; x < sw - 1; ++x) {
            int gx = -small[(y - 1) * sw + (x - 1)] + small[(y - 1) * sw + (x + 1)]
                     - 2 * small[y * sw + (x - 1)] + 2 * small[y * sw + (x + 1)]
                     - small[(y + 1) * sw + (x - 1)] + small[(y + 1) * sw + (x + 1)];

            int gy = -small[(y - 1) * sw + (x - 1)] - 2 * small[(y - 1) * sw + x] - small[(y - 1) * sw + (x + 1)]
                     + small[(y + 1) * sw + (x - 1)] + 2 * small[(y + 1) * sw + x] + small[(y + 1) * sw + (x + 1)];

            int mag = std::abs(gx) + std::abs(gy);
            grad[y * sw + x] = mag;
            if (mag > maxGrad) maxGrad = mag;
        }
    }

    int threshold = maxGrad * 25 / 100;
    std::vector<Point2D> edgePoints;
    edgePoints.reserve(sw * sh / 8);

    for (int y = 5; y < sh - 5; ++y) {
        for (int x = 5; x < sw - 5; ++x) {
            if (grad[y * sw + x] > threshold) {
                edgePoints.push_back({static_cast<float>(x * downsample), static_cast<float>(y * downsample)});
            }
        }
    }

    if (edgePoints.size() < 4) {
        return fallback;
    }

    // Find extremal points in 4 quadrants (TL, TR, BR, BL)
    // TL: min (x + y), TR: max (x - y), BR: max (x + y), BL: min (x - y)
    Point2D bestTL = edgePoints[0], bestTR = edgePoints[0];
    Point2D bestBR = edgePoints[0], bestBL = edgePoints[0];
    float minSum = 1e9f, maxSum = -1e9f;
    float minDiff = 1e9f, maxDiff = -1e9f;

    for (const auto& pt : edgePoints) {
        float sum = pt.x + pt.y;
        float diff = pt.x - pt.y;

        if (sum < minSum) { minSum = sum; bestTL = pt; }
        if (sum > maxSum) { maxSum = sum; bestBR = pt; }
        if (diff > maxDiff) { maxDiff = diff; bestTR = pt; }
        if (diff < minDiff) { minDiff = diff; bestBL = pt; }
    }

    // Sanity check: Ensure detected quad is plausible and convex
    float areaW = std::max(bestTR.x - bestTL.x, bestBR.x - bestBL.x);
    float areaH = std::max(bestBL.y - bestTL.y, bestBR.y - bestTR.y);

    if (areaW < width * 0.25f || areaH < height * 0.25f) {
        return fallback;
    }

    // Cross product convexity check
    auto crossProduct = [](const Point2D& p0, const Point2D& p1, const Point2D& p2) -> float {
        return (p1.x - p0.x) * (p2.y - p1.y) - (p1.y - p0.y) * (p2.x - p1.x);
    };

    float cp1 = crossProduct(bestTL, bestTR, bestBR);
    float cp2 = crossProduct(bestTR, bestBR, bestBL);
    float cp3 = crossProduct(bestBR, bestBL, bestTL);
    float cp4 = crossProduct(bestBL, bestTL, bestTR);

    bool isConvex = (cp1 > 0 && cp2 > 0 && cp3 > 0 && cp4 > 0) || (cp1 < 0 && cp2 < 0 && cp3 < 0 && cp4 < 0);
    if (!isConvex) {
        return fallback;
    }

    return {bestTL, bestTR, bestBR, bestBL};
}
