import { test, expect } from '@playwright/test'

test('track submitted flow history', async ({ page }) => {
  await page.goto('/')
  await page.waitForURL(/localhost:8180/)
  await page.fill('#username', 'alice')
  await page.fill('#password', 'password')
  await page.click('[type=submit]')
  await page.waitForURL(/localhost:5173/)

  await page.goto('/submissions')
  await expect(page.getByRole('heading', { name: /submissions/i })).toBeVisible()

  // Click the first flow
  const firstRow = page.getByRole('row').nth(1)
  await expect(firstRow).toBeVisible({ timeout: 5000 })
  await firstRow.getByRole('link').click()
  await page.waitForURL(/\/flows\//)

  // Should see audit timeline events
  await expect(page.getByText(/FLOW_STARTED/i)).toBeVisible()
})
