/**
 * Render thật từng component với dữ liệu giả, bắt loại lỗi mà `npm run lint` và `vite build`
 * đều bỏ lọt: gọi một hàm không tồn tại, đọc thuộc tính của `undefined`, dùng biến còn sót sau
 * một lần đổi tên — ở đúng nhánh giao diện hiếm khi hiển thị.
 *
 * Vì sao cần: lint bắt biến không tồn tại và import sai tên, nhưng không biết gì về nhánh nào
 * thực sự chạy được. Đã có lần trang Outfit vỡ ở đúng nhánh "bộ đang vướng đồ giặt" — nhánh chỉ
 * hiện ra khi tủ đồ có món đang giặt.
 *
 * `useEffect` không chạy khi render phía server nên không có lời gọi API nào; thứ được kiểm là
 * đường render đầu tiên. Đây là lưới an toàn rẻ tiền, không phải bộ test giao diện đầy đủ.
 *
 * Chạy: npm run smoke
 */
import React from 'react';
import { renderToString } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';

import ItemCard from '../src/components/ItemCard';
import OutfitCard from '../src/components/OutfitCard';
import CalendarPlanChip from '../src/components/CalendarPlanChip';
import OutfitVisual from '../src/components/OutfitVisual';
import { ConfirmProvider } from '../src/context/ConfirmContext';

const failures = [];
let passed = 0;

function check(name, element) {
  try {
    renderToString(
      <MemoryRouter>
        <ConfirmProvider>{element}</ConfirmProvider>
      </MemoryRouter>
    );
    passed += 1;
  } catch (error) {
    failures.push(`${name}\n    ${error.name}: ${error.message}`);
  }
}

const noop = () => {};
const handlers = {
  onOpen: noop,
  onEdit: noop,
  onDelete: noop,
  onToggleFavorite: noop,
  onWear: noop,
  onWashed: noop,
};

const item = (extra = {}) => ({
  id: 1,
  name: 'Áo sơ mi trắng',
  color: 'Trắng',
  colorTone: 'NEUTRAL',
  category: 'TOP',
  season: 'ALL_SEASON',
  style: 'FORMAL',
  condition: 'GOOD',
  status: 'AVAILABLE',
  wearCount: 4,
  lastWornAt: null,
  favorite: false,
  imageUrl: 'https://cdn.test/a.jpg',
  archived: false,
  blockReason: null,
  ...extra,
});

const outfit = (extra = {}) => ({
  id: 1,
  name: 'Bộ đi làm',
  description: 'Lịch sự',
  imageUrl: 'https://cdn.test/outfit.jpg',
  season: 'ALL_SEASON',
  style: 'FORMAL',
  favorite: false,
  wearCount: 3,
  lastWornAt: null,
  clothingItems: [item(), item({ id: 2, name: 'Quần âu', category: 'BOTTOM' })],
  available: true,
  archivedItemNames: [],
  wearableNow: true,
  blockingItems: [],
  ...extra,
});

// --- Món đồ: mọi lý do chưa mặc được đều phải render ---
check('ItemCard / mặc được', <ItemCard item={item()} {...handlers} />);
check('ItemCard / đang giặt', <ItemCard item={item({ status: 'LAUNDRY', blockReason: 'LAUNDRY' })} {...handlers} />);
check('ItemCard / hư hỏng', <ItemCard item={item({ condition: 'DAMAGED', blockReason: 'DAMAGED' })} {...handlers} />);
check(
  'ItemCard / chưa dùng được',
  <ItemCard item={item({ status: 'UNAVAILABLE', blockReason: 'UNAVAILABLE' })} {...handlers} />
);
check('ItemCard / không ảnh', <ItemCard item={item({ imageUrl: null })} {...handlers} />);

// --- Outfit: đây là nhánh đã từng làm vỡ trang ---
check('OutfitCard / mặc được', <OutfitCard outfit={outfit()} {...handlers} />);
check(
  'OutfitCard / vướng đồ giặt',
  <OutfitCard
    outfit={outfit({ wearableNow: false, blockingItems: [{ itemName: 'Áo sơ mi trắng', reason: 'LAUNDRY' }] })}
    {...handlers}
  />
);
check(
  'OutfitCard / thiếu món',
  <OutfitCard
    outfit={outfit({
      available: false,
      archivedItemNames: ['Áo sơ mi trắng'],
      wearableNow: false,
      blockingItems: [{ itemName: 'Áo sơ mi trắng', reason: 'ARCHIVED' }],
    })}
    {...handlers}
  />
);
check(
  'OutfitCard / nhiều lý do cùng lúc',
  <OutfitCard
    outfit={outfit({
      wearableNow: false,
      blockingItems: [
        { itemName: 'Áo sơ mi trắng', reason: 'LAUNDRY' },
        { itemName: 'Quần âu', reason: 'LAUNDRY' },
        { itemName: 'Giày da', reason: 'DAMAGED' },
      ],
    })}
    {...handlers}
  />
);
check('OutfitCard / không ảnh', <OutfitCard outfit={outfit({ imageUrl: null })} {...handlers} />);

// Server cũ chưa trả các trường mới: giao diện phải chịu được thay vì trắng trang.
check(
  'OutfitCard / thiếu trường mới',
  <OutfitCard outfit={{ id: 9, name: 'Bộ cũ', clothingItems: [item()], available: true }} {...handlers} />
);
check('ItemCard / thiếu trường mới', <ItemCard item={{ id: 9, name: 'Món cũ' }} {...handlers} />);

check('OutfitVisual', <OutfitVisual outfit={outfit()} />);
check(
  'CalendarPlanChip',
  <CalendarPlanChip plan={{ id: 1, date: '2026-08-03', outfit: outfit(), note: 'Ghi chú', completed: false }} />
);

if (failures.length > 0) {
  console.error(`\n✗ ${failures.length} lỗi render:\n`);
  failures.forEach((line) => console.error(`  ${line}\n`));
  process.exit(1);
}

console.log(`✓ ${passed} trường hợp render sạch.`);
