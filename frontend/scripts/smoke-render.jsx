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
import AiPlanModal from '../src/components/AiPlanModal';
import WardrobeTransferModal from '../src/components/WardrobeTransferModal';
import DayModal from '../src/components/DayModal';
import AiPrompt from '../src/components/AiPrompt';
import ForecastChip from '../src/components/ForecastChip';
import OutfitActions from '../src/components/OutfitActions';

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

// --- Lịch: hộp thoại một ngày, gồm nhánh "đã lên lịch nhưng hôm nay chưa mặc được" ---
const dayPlan = (extra = {}) => ({
  id: 1,
  date: '2026-08-03',
  outfit: outfit(),
  note: 'Họp với khách',
  completed: false,
  ...extra,
});

const dayModalProps = { iso: '2026-08-03', onChanged: noop, onClose: noop };

check('DayModal / ngày trống', <DayModal {...dayModalProps} plans={[]} outfits={[outfit()]} />);
check(
  'DayModal / có kế hoạch',
  <DayModal {...dayModalProps} plans={[dayPlan()]} outfits={[outfit()]} />
);
check(
  'DayModal / bộ vướng đồ giặt',
  <DayModal
    {...dayModalProps}
    plans={[
      dayPlan({
        outfit: outfit({ wearableNow: false, blockingItems: [{ itemName: 'Áo sơ mi trắng', reason: 'LAUNDRY' }] }),
      }),
    ]}
    outfits={[outfit()]}
  />
);
check(
  'DayModal / đã đánh dấu mặc',
  <DayModal {...dayModalProps} plans={[dayPlan({ completed: true })]} outfits={[outfit()]} />
);
// Server cũ chưa trả wearableNow: nút không được vô cớ khóa lại.
check(
  'DayModal / thiếu trường mới',
  <DayModal
    {...dayModalProps}
    plans={[{ id: 9, date: '2026-08-03', outfit: { id: 9, name: 'Bộ cũ', clothingItems: [item()] } }]}
    outfits={[outfit()]}
  />
);

check('AiPlanModal / form', <AiPlanModal forecast={[]} tone={null} onSaved={noop} onClose={noop} />);

// --- Nhập/xuất tủ đồ: khối báo cáo chỉ hiện sau khi nhập nên rất dễ vỡ mà không ai thấy ---
check(
  'WardrobeTransferModal',
  <WardrobeTransferModal onImported={noop} onClose={noop} />
);

// --- Trang Gợi ý: các mảnh của vùng kết quả có tab ---
check(
  'ForecastChip',
  <ForecastChip day={{ date: '2026-08-03', emoji: '⛅', tempMin: 25.4, tempMax: 33.1, rainChance: 20 }} />
);
// Server cũ / API thiếu rainChance: dải dự báo không được vỡ vì một ô trống.
check(
  'ForecastChip / thiếu rainChance',
  <ForecastChip day={{ date: '2026-08-03', emoji: '☀️', tempMin: 26, tempMax: 34 }} />
);
check('OutfitActions / chưa mặc', <OutfitActions outfit={outfit()} onPlan={noop} onWear={noop} />);
check(
  'OutfitActions / đã mặc hôm nay',
  <OutfitActions outfit={outfit({ lastWornAt: new Date().toISOString() })} onPlan={noop} onWear={noop} />
);
check(
  'AiPrompt',
  <AiPrompt
    emoji="🤖"
    title="Để AI chọn giúp"
    hint="Mô tả ngắn"
    actionLabel="Cho AI chọn"
    loading={false}
    loadingLabel="Đang chọn..."
    onRun={noop}
  />
);

if (failures.length > 0) {
  console.error(`\n✗ ${failures.length} lỗi render:\n`);
  failures.forEach((line) => console.error(`  ${line}\n`));
  process.exit(1);
}

console.log(`✓ ${passed} trường hợp render sạch.`);
