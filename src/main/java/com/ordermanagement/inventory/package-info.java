/**
 * Module {@code inventory}: danh mục sản phẩm và tồn kho (bảng {@code products}, {@code inventory}).
 *
 * <p>Sản phẩm nằm trong module này vì cấu trúc dự án không có module {@code product} riêng; hai phần
 * dùng chung dữ liệu và luôn được thao tác cùng nhau khi đặt hàng.
 *
 * <ul>
 *   <li>{@code api}: controller và request DTO (REST).</li>
 *   <li>{@code application}: use case ({@code ProductService}, {@code InventoryService}) và view.
 *       Đây là phần duy nhất module khác được phép dùng.</li>
 *   <li>{@code domain}: entity {@code Product}, {@code Inventory}.</li>
 *   <li>{@code infrastructure}: Spring Data JPA repository và câu UPDATE có điều kiện cho tồn kho.</li>
 * </ul>
 */
package com.ordermanagement.inventory;
